package org.laopopo.base.registry;

import static org.laopopo.common.serialization.SerializerHolder.serializerImpl;
import static org.laopopo.common.utils.Constants.ACK_OPERATION_FAILURE;
import static org.laopopo.common.utils.Constants.ACK_OPERATION_SUCCESS;
import static org.laopopo.common.utils.Constants.ACK_PUBLISH_CANCEL_FAILURE;
import static org.laopopo.common.utils.Constants.ACK_PUBLISH_CANCEL_SUCCESS;
import static org.laopopo.common.utils.Constants.ACK_PUBLISH_FAILURE;
import static org.laopopo.common.utils.Constants.ACK_PUBLISH_SUCCESS;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ConcurrentSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.laopopo.common.exception.remoting.RemotingSendRequestException;
import org.laopopo.common.exception.remoting.RemotingTimeoutException;
import org.laopopo.common.metrics.ServiceMetrics;
import org.laopopo.common.metrics.ServiceMetrics.ConsumerInfo;
import org.laopopo.common.metrics.ServiceMetrics.ProviderInfo;
import org.laopopo.common.protocal.LaopopoProtocol;
import org.laopopo.common.rpc.RegisterMeta;
import org.laopopo.common.rpc.RegisterMeta.Address;
import org.laopopo.common.rpc.ServiceReviewState;
import org.laopopo.common.transport.body.AckCustomBody;
import org.laopopo.common.transport.body.MetricsRequestCustomBody;
import org.laopopo.common.transport.body.PublishServiceCustomBody;
import org.laopopo.common.transport.body.RegistryMetricsCustomBody;
import org.laopopo.common.transport.body.ReviewServiceCustomBody;
import org.laopopo.common.transport.body.SubcribeResultCustomBody;
import org.laopopo.common.transport.body.SubscribeRequestCustomBody;
import org.laopopo.remoting.ConnectionUtils;
import org.laopopo.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author BazingaLyn
 * @description 注册服务中心端的provider侧的管理
 * 
 * @notice 如果用户在monitor端配置某个服务的权重，某个服务的审核，某个服务的手动降级 等操作，默认1分钟后生效
 * @time 2016年8月15日
 * @modifytime
 */
public class RegistryProviderManager implements RegistryProviderServer {

	private static final Logger logger = LoggerFactory.getLogger(RegistryProviderManager.class);

	private static final AttributeKey<ConcurrentSet<String>> S_SUBSCRIBE_KEY = AttributeKey.valueOf("server.subscribed");
	private static final AttributeKey<ConcurrentSet<RegisterMeta>> S_PUBLISH_KEY = AttributeKey.valueOf("server.published");

	private DefaultRegistryServer defaultRegistryServer;

	private final ConcurrentMap<String, ConcurrentMap<Address, RegisterMeta>> globalRegisterInfoMap = new ConcurrentHashMap<String, ConcurrentMap<Address, RegisterMeta>>();

	// 指定节点都注册了哪些服务
	private final ConcurrentMap<Address, ConcurrentSet<String>> globalServiceMetaMap = new ConcurrentHashMap<RegisterMeta.Address, ConcurrentSet<String>>();

	private final ConcurrentMap<String, ConcurrentSet<Channel>> globalConsumerMetaMap = new ConcurrentHashMap<String, ConcurrentSet<Channel>>();

	private final ConcurrentHashMap<Address, Channel> globalProviderChannelMetaMap = new ConcurrentHashMap<RegisterMeta.Address, Channel>();

	public RegistryProviderManager(DefaultRegistryServer defaultRegistryServer) {
		this.defaultRegistryServer = defaultRegistryServer;
	}

	/**
	 * 处理provider服务注册
	 * 
	 * @throws InterruptedException
	 * @throws RemotingTimeoutException
	 * @throws RemotingSendRequestException
	 */
	@Override
	public RemotingTransporter handlerRegister(RemotingTransporter remotingTransporter, Channel channel) throws RemotingSendRequestException,
			RemotingTimeoutException, InterruptedException {

		// 准备好ack信息返回个provider，悲观主义，默认返回失败ack，要求provider重新发送请求
		AckCustomBody ackCustomBody = new AckCustomBody(remotingTransporter.getOpaque(), false, ACK_PUBLISH_FAILURE);
		RemotingTransporter responseTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.ACK, ackCustomBody,
				remotingTransporter.getOpaque());

		// 接收到主体信息
		PublishServiceCustomBody publishServiceCustomBody = serializerImpl().readObject(remotingTransporter.bytes(), PublishServiceCustomBody.class);

		RegisterMeta meta = RegisterMeta.createRegiserMeta(publishServiceCustomBody);

		if (logger.isDebugEnabled()) {
			logger.info("Publish [{}] on channel[{}].", meta, channel);
		}

		// channel上打上该服务的标记 方便当channel inactive的时候，直接从channel上拿到标记的属性，通知
		attachPublishEventOnChannel(meta, channel);

		// 一个服务的最小单元，也是确定一个服务的最小单位
		final String serviceName = meta.getServiceName();
		// 找出提供此服务的全部地址和该服务在该地址下的审核情况
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceName);

		synchronized (globalRegisterInfoMap) {

			// 获取到这个地址可能以前注册过的注册信息
			RegisterMeta existRegiserMeta = maps.get(meta.getAddress());

			// 如果等于空，则说明以前没有注册过
			if (null == existRegiserMeta) {
				existRegiserMeta = meta;
				maps.put(meta.getAddress(), existRegiserMeta);
			}

			this.getServiceMeta(meta.getAddress()).add(serviceName);

			// 判断provider发送的信息已经被成功的存储的情况下，则告之服务注册成功
			ackCustomBody.setDesc(ACK_PUBLISH_SUCCESS);
			ackCustomBody.setSuccess(true);

			// 如果审核通过，则通知相关服务的订阅者
			if (meta.getIsReviewed() == ServiceReviewState.PASS_REVIEW) {
				this.defaultRegistryServer.getConsumerManager().notifyMacthedSubscriber(meta);
			}
		}

		globalProviderChannelMetaMap.put(meta.getAddress(), channel);

		return responseTransporter;
	}

	public RemotingTransporter handleMetricsService(RemotingTransporter request, Channel channel) {

		MetricsRequestCustomBody body = serializerImpl().readObject(request.bytes(), MetricsRequestCustomBody.class);

		RegistryMetricsCustomBody responseBody = new RegistryMetricsCustomBody();
		RemotingTransporter remotingTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.METRICS_SERVICE, responseBody,
				request.getOpaque());
		List<ServiceMetrics> serviceMetricses = new ArrayList<ServiceMetrics>();
		// 统计全部
		if (body.getServiceName() == null) {

			if (globalServiceMetaMap.keySet() != null) {

				for (String serviceName : globalRegisterInfoMap.keySet()) {
					ServiceMetrics serviceMetrics = assemblyServiceMetricsByServiceName(serviceName);
					serviceMetricses.add(serviceMetrics);
				}
			}
		} else { // 即使更新的服务
			String serviceName = body.getServiceName();
			ServiceMetrics serviceMetrics = assemblyServiceMetricsByServiceName(serviceName);
			serviceMetricses.add(serviceMetrics);

		}
		responseBody.setServiceMetricses(serviceMetricses);
		return remotingTransporter;
	}

	private ServiceMetrics assemblyServiceMetricsByServiceName(String serviceName) {
		ServiceMetrics serviceMetrics = new ServiceMetrics();
		serviceMetrics.setServiceName(serviceName);
		ConcurrentMap<Address, RegisterMeta> concurrentMap = globalRegisterInfoMap.get(serviceName);
		if (null != concurrentMap && concurrentMap.keySet() != null) {
			List<ProviderInfo> providerInfos = new ArrayList<ServiceMetrics.ProviderInfo>();
			for (Address address : concurrentMap.keySet()) {

				ProviderInfo providerInfo = new ProviderInfo();
				providerInfo.setPort(address.getPort());
				providerInfo.setHost(address.getHost());
				RegisterMeta meta = concurrentMap.get(address);
				providerInfo.setServiceReviewState(meta.getIsReviewed());
				providerInfo.setIsDegradeService(meta.isHasDegradeService());
				providerInfo.setIsVipService(meta.isVIPService());
				providerInfo.setIsSupportDegrade(meta.isSupportDegradeService());

				providerInfos.add(providerInfo);
			}
			serviceMetrics.setProviderInfos(providerInfos);
		}
		ConcurrentSet<Channel> channels = globalConsumerMetaMap.get(serviceName);
		if (null != channels && channels.size() > 0) {
			List<ConsumerInfo> consumerInfos = new ArrayList<ServiceMetrics.ConsumerInfo>();
			for (Channel consumerChannel : channels) {
				ConsumerInfo consumerInfo = new ConsumerInfo();
				String consumerAddress = ConnectionUtils.parseChannelRemoteAddr(consumerChannel);
				if (!"".equals(consumerAddress) && null != consumerAddress) {
					String[] s = consumerAddress.split(":");
					consumerInfo.setHost(s[0]);
					consumerInfo.setPort(Integer.parseInt(s[1]));
					consumerInfos.add(consumerInfo);
				}
			}
			serviceMetrics.setConsumerInfos(consumerInfos);
		}
		return serviceMetrics;
	}

	public RemotingTransporter handleDegradeService(RemotingTransporter request, Channel channel) throws RemotingSendRequestException,
			RemotingTimeoutException, InterruptedException {

		AckCustomBody ackCustomBody = new AckCustomBody(request.getOpaque(), false, ACK_OPERATION_FAILURE);
		RemotingTransporter remotingTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.ACK, ackCustomBody, request.getOpaque());

		ReviewServiceCustomBody body = serializerImpl().readObject(request.bytes(), ReviewServiceCustomBody.class);

		String serviceName = body.getSerivceName();
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceName);

		Address address = null;

		synchronized (globalRegisterInfoMap) {

			RegisterMeta existRegiserMeta = maps.get(body.getAddress());
			if (null == existRegiserMeta) {
				return remotingTransporter;
			}
			if (existRegiserMeta.getIsReviewed() != ServiceReviewState.PASS_REVIEW) {
				return remotingTransporter;
			}

			address = existRegiserMeta.getAddress();
		}

		Channel matchedProviderChannel = globalProviderChannelMetaMap.get(address);

		return defaultRegistryServer.getRemotingServer().invokeSync(matchedProviderChannel, request, 3000l);
	}

	/**
	 * provider端发送的请求，取消对某个服务的提供
	 * 
	 * @param request
	 * @param channel
	 * @return
	 * @throws InterruptedException
	 * @throws RemotingTimeoutException
	 * @throws RemotingSendRequestException
	 */
	public RemotingTransporter handlerRegisterCancel(RemotingTransporter request, Channel channel) throws RemotingSendRequestException,
			RemotingTimeoutException, InterruptedException {

		// 准备好ack信息返回个provider，悲观主义，默认返回失败ack，要求provider重新发送请求
		AckCustomBody ackCustomBody = new AckCustomBody(request.getOpaque(), false, ACK_PUBLISH_CANCEL_FAILURE);
		RemotingTransporter responseTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.ACK, ackCustomBody, request.getOpaque());

		// 接收到主体信息
		PublishServiceCustomBody publishServiceCustomBody = serializerImpl().readObject(request.bytes(), PublishServiceCustomBody.class);

		RegisterMeta meta = RegisterMeta.createRegiserMeta(publishServiceCustomBody);

		handlePublishCancel(meta, channel);

		ackCustomBody.setDesc(ACK_PUBLISH_CANCEL_SUCCESS);
		ackCustomBody.setSuccess(true);

		globalProviderChannelMetaMap.remove(meta.getAddress());

		return responseTransporter;
	}

	/**
	 * 处理consumer的消息订阅，并返回结果
	 * 
	 * @param request
	 * @param channel
	 * @return
	 */
	public RemotingTransporter handleSubscribe(RemotingTransporter request, Channel channel) {

		SubcribeResultCustomBody subcribeResultCustomBody = new SubcribeResultCustomBody();
		RemotingTransporter responseTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.SUBCRIBE_RESULT, subcribeResultCustomBody,
				request.getOpaque());
		// 接收到主体信息
		SubscribeRequestCustomBody requestCustomBody = serializerImpl().readObject(request.bytes(), SubscribeRequestCustomBody.class);
		String serviceName = requestCustomBody.getServiceName();
		// 将其降入到channel的group中去
		this.defaultRegistryServer.getConsumerManager().getSubscriberChannels().add(channel);

		// 存入到消费者中全局变量中去 TODO is need?
		ConcurrentSet<Channel> channels = globalConsumerMetaMap.get(serviceName);
		if (null == channels) {
			channels = new ConcurrentSet<Channel>();
		}
		channels.add(channel);

		attachSubscribeEventOnChannel(serviceName, channel);

		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceName);
		// 如果订阅的暂时还没有服务提供者，则返回空列表给订阅者
		if (maps.isEmpty()) {
			return responseTransporter;
		}

		buildSubcribeResultCustomBody(maps, subcribeResultCustomBody);

		return responseTransporter;
	}

	/***
	 * 服务下线的接口
	 * 
	 * @param meta
	 * @param channel
	 * @throws InterruptedException
	 * @throws RemotingTimeoutException
	 * @throws RemotingSendRequestException
	 */
	public void handlePublishCancel(RegisterMeta meta, Channel channel) throws RemotingSendRequestException, RemotingTimeoutException, InterruptedException {

		if (logger.isDebugEnabled()) {
			logger.info("Cancel publish {} on channel{}.", meta, channel);
		}

		attachPublishCancelEventOnChannel(meta, channel);

		final String serviceMeta = meta.getServiceName();
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceMeta);
		if (maps.isEmpty()) {
			return;
		}

		synchronized (globalRegisterInfoMap) {

			Address address = meta.getAddress();
			RegisterMeta data = maps.remove(address);

			if (data != null) {
				this.getServiceMeta(address).remove(serviceMeta);

				if (data.getIsReviewed() == ServiceReviewState.PASS_REVIEW)
					this.defaultRegistryServer.getConsumerManager().notifyMacthedSubscriberCancel(meta);
			}
		}
	}

	/**
	 * 审核服务
	 * 
	 * @param request
	 * @param channel
	 * @return
	 */
	public RemotingTransporter handleReview(RemotingTransporter request, Channel channel) {

		AckCustomBody ackCustomBody = new AckCustomBody(request.getOpaque(), false, ACK_OPERATION_FAILURE);
		RemotingTransporter remotingTransporter = RemotingTransporter.createResponseTransporter(LaopopoProtocol.ACK, ackCustomBody, request.getOpaque());

		if (logger.isDebugEnabled()) {
			logger.info("review service {} on channel{}.", request, channel);
		}

		ReviewServiceCustomBody body = serializerImpl().readObject(request.bytes(), ReviewServiceCustomBody.class);

		// 获取到这个服务的所有
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(body.getSerivceName());

		if (maps.isEmpty()) {
			return remotingTransporter;
		}

		synchronized (globalRegisterInfoMap) {

			RegisterMeta data = maps.get(body.getAddress());

			if (data != null) {
				ackCustomBody.setDesc(ACK_OPERATION_SUCCESS);
				ackCustomBody.setSuccess(true);
				data.setIsReviewed(body.getServiceReviewState());
			}
		}

		return remotingTransporter;
	}

	/*
	 * ======================================分隔符，以上为核心方法，下面为内部方法==================
	 * ============
	 */

	private void attachPublishCancelEventOnChannel(RegisterMeta meta, Channel channel) {
		Attribute<ConcurrentSet<RegisterMeta>> attr = channel.attr(S_PUBLISH_KEY);
		ConcurrentSet<RegisterMeta> registerMetaSet = attr.get();
		if (registerMetaSet == null) {
			ConcurrentSet<RegisterMeta> newRegisterMetaSet = new ConcurrentSet<>();
			registerMetaSet = attr.setIfAbsent(newRegisterMetaSet);
			if (registerMetaSet == null) {
				registerMetaSet = newRegisterMetaSet;
			}
		}

		registerMetaSet.remove(meta);
	}

	private void attachPublishEventOnChannel(RegisterMeta meta, Channel channel) {

		Attribute<ConcurrentSet<RegisterMeta>> attr = channel.attr(S_PUBLISH_KEY);
		ConcurrentSet<RegisterMeta> registerMetaSet = attr.get();
		if (registerMetaSet == null) {
			ConcurrentSet<RegisterMeta> newRegisterMetaSet = new ConcurrentSet<>();
			registerMetaSet = attr.setIfAbsent(newRegisterMetaSet);
			if (registerMetaSet == null) {
				registerMetaSet = newRegisterMetaSet;
			}
		}

		registerMetaSet.add(meta);
	}

	private ConcurrentSet<String> getServiceMeta(Address address) {
		ConcurrentSet<String> serviceMetaSet = globalServiceMetaMap.get(address);
		if (serviceMetaSet == null) {
			ConcurrentSet<String> newServiceMetaSet = new ConcurrentSet<>();
			serviceMetaSet = globalServiceMetaMap.putIfAbsent(address, newServiceMetaSet);
			if (serviceMetaSet == null) {
				serviceMetaSet = newServiceMetaSet;
			}
		}
		return serviceMetaSet;
	}

	private ConcurrentMap<Address, RegisterMeta> getRegisterMeta(String serviceMeta) {
		ConcurrentMap<Address, RegisterMeta> maps = globalRegisterInfoMap.get(serviceMeta);
		if (maps == null) {
			ConcurrentMap<Address, RegisterMeta> newMaps = new ConcurrentHashMap<RegisterMeta.Address, RegisterMeta>();
			maps = globalRegisterInfoMap.putIfAbsent(serviceMeta, newMaps);
			if (maps == null) {
				maps = newMaps;
			}
		}
		return maps;
	}

	private void buildSubcribeResultCustomBody(ConcurrentMap<Address, RegisterMeta> maps, SubcribeResultCustomBody subcribeResultCustomBody) {

		Collection<RegisterMeta> values = maps.values();

		if (values != null && values.size() > 0) {
			List<RegisterMeta> registerMetas = new ArrayList<RegisterMeta>();
			for (RegisterMeta meta : values) {
				// 判断是否人工审核过，审核过的情况下，组装给consumer的响应主体，返回个consumer
				if (meta.getIsReviewed() == ServiceReviewState.PASS_REVIEW) {
					registerMetas.add(meta);
				}
			}
			subcribeResultCustomBody.setRegisterMeta(registerMetas);
		}
	}

	private void attachSubscribeEventOnChannel(String serviceMeta, Channel channel) {
		Attribute<ConcurrentSet<String>> attr = channel.attr(S_SUBSCRIBE_KEY);
		ConcurrentSet<String> serviceMetaSet = attr.get();
		if (serviceMetaSet == null) {
			ConcurrentSet<String> newServiceMetaSet = new ConcurrentSet<String>();
			serviceMetaSet = attr.setIfAbsent(newServiceMetaSet);
			if (serviceMetaSet == null) {
				serviceMetaSet = newServiceMetaSet;
			}
		}
		serviceMetaSet.add(serviceMeta);
	}

	public ConcurrentMap<String, ConcurrentMap<Address, RegisterMeta>> getGlobalRegisterInfoMap() {
		return globalRegisterInfoMap;
	}

	public ConcurrentMap<Address, ConcurrentSet<String>> getGlobalServiceMetaMap() {
		return globalServiceMetaMap;
	}

}
