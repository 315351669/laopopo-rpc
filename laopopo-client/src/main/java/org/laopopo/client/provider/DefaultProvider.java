package org.laopopo.client.provider;

import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.laopopo.client.provider.flow.control.FlowController;
import org.laopopo.client.provider.model.DefaultProviderInactiveProcessor;
import org.laopopo.common.exception.remoting.RemotingException;
import org.laopopo.common.protocal.LaopopoProtocol;
import org.laopopo.common.utils.NamedThreadFactory;
import org.laopopo.remoting.ConnectionUtils;
import org.laopopo.remoting.model.RemotingTransporter;
import org.laopopo.remoting.netty.NettyClientConfig;
import org.laopopo.remoting.netty.NettyRemotingClient;
import org.laopopo.remoting.netty.NettyRemotingServer;
import org.laopopo.remoting.netty.NettyServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author BazingaLyn
 * @description 服务提供者端的具体实现
 * @time 2016年8月16日
 * @modifytime 2016年8月23日
 */
public class DefaultProvider implements Provider {

	private static final Logger logger = LoggerFactory.getLogger(DefaultProvider.class);

	
	private NettyClientConfig clientConfig;//向注册中心连接的netty client配置
	private NettyServerConfig serverConfig;//等待服务提供者连接的netty server的配置
	private NettyRemotingClient nettyRemotingClient;// 连接monitor和注册中心
	private NettyRemotingServer nettyRemotingServer;// 等待被Consumer连接
	private NettyRemotingServer nettyRemotingVipServer;// 等待被Consumer连接
	private ProviderRegistryController providerController;//provider端向注册中心连接的业务逻辑的控制器
	private ProviderRPCController providerRPCController;
	private ExecutorService remotingExecutor; //RPC调用的核心线程执行器
	private ExecutorService remotingVipExecutor; //RPC调用的核心线程执行器

	/********* 要发布的服务的信息 ***********/
	private List<RemotingTransporter> publishRemotingTransporters;
	/***** 注册中心的地址 ******/
	private String registryAddress;
	/******* 服务暴露给consumer的地址 ********/
	private String serviceListenAddress;
	/********* 该机器实例提供的全局限流器 ************/
	private FlowController globalController;
    /***********要提供的服务***************/
	private Object[] obj;
	
	//当前provider端状态是否健康，也就是说如果注册宕机后，该provider端的实例信息是失效，这是需要重新发送注册信息,因为默认状态下start就是发送，只有channel inactive的时候说明短线了，需要重新发布信息
	private boolean ProviderStateIsHealthy = true;

	// 定时任务
	private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("provider-timer"));

	public DefaultProvider(NettyClientConfig clientConfig, NettyServerConfig serverConfig) {
		this.clientConfig = clientConfig;
		this.serverConfig = serverConfig;
		providerController = new ProviderRegistryController(this);
		providerRPCController = new ProviderRPCController(this);
		initialize();
	}

	private void initialize() {

		this.nettyRemotingServer = new NettyRemotingServer(this.serverConfig);
		this.nettyRemotingClient = new NettyRemotingClient(this.clientConfig);
        this.nettyRemotingVipServer = new NettyRemotingServer(this.serverConfig);
		
		this.remotingExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads(), new NamedThreadFactory("providerExecutorThread_"));
		this.remotingVipExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads() / 2, new NamedThreadFactory("providerExecutorThread_"));
		// 注册处理器
		this.registerProcessor();

		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				// 延迟5秒，每隔60秒开始 像其发送注册服务信息
				try {
					if(!ProviderStateIsHealthy){
						DefaultProvider.this.publishedAndStartProvider();
					}
				} catch (Exception e) {
					logger.warn("schedule publish failed [{}]",e.getMessage());
				} 
			}
		}, 60, 60, TimeUnit.SECONDS);

	}

	private void registerProcessor() {
		//provider端作为client端去连接registry注册中心的处理器
		this.nettyRemotingClient.registerProcessor(LaopopoProtocol.DEGRADE_SERVICE, new DefaultProviderRegistryProcessor(this), null);
		this.nettyRemotingClient.registerChannelInactiveProcessor(new DefaultProviderInactiveProcessor(this), null);
		//provider端作为netty的server端去等待调用者连接的处理器，此处理器只处理RPC请求
		this.nettyRemotingServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this), this.remotingExecutor);
		this.nettyRemotingVipServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this), this.remotingVipExecutor);
	}

	public List<RemotingTransporter> getPublishRemotingTransporters() {
		return publishRemotingTransporters;
	}


	@Override
	public void publishedAndStartProvider() throws InterruptedException, RemotingException {
		
		logger.info("publish service....");
		providerController.getRegistryController().publishedAndStartProvider();
	}

	@Override
	public Provider publishService(Object... obj) {
		this.obj = obj;
		return this;
	}
	
	@Override
	public void handlerRPCRequest(RemotingTransporter request, Channel channel) {
		providerRPCController.handlerRPCRequest(request,channel);
	}

	/**
	 * 设置暴露服务的地址
	 * 
	 * @param serviceListenAddress
	 * @return
	 */
	@Override
	public Provider globalController(FlowController globalController) {
		this.globalController = globalController;
		return this;
	}

	@Override
	public Provider serviceListenAddress(String serviceListenAddress) {
		this.serviceListenAddress = serviceListenAddress;
		return this;
	}

	@Override
	public Provider registryAddress(String registryAddress) {
		this.registryAddress = registryAddress;
		return this;
	}

	@Override
	public void start() throws InterruptedException, RemotingException {

		//编织服务
		this.publishRemotingTransporters = providerController.getLocalServerWrapperManager().wrapperRegisterInfo(this.getServiceListenAddress(),
				this.getGlobalController(), this.obj);
		
		logger.info("registry center address [{}] serviceAddress [{}] service [{}]", this.registryAddress, this.serviceListenAddress,
				this.publishRemotingTransporters);
		
		nettyRemotingClient.start();
		//发布任务
		this.publishedAndStartProvider();
		logger.info("provider start successfully");
		
		if(serviceListenAddress != null){
			int port = ConnectionUtils.getPortFromAddress(serviceListenAddress);
			this.serverConfig.setListenPort(port);
			this.nettyRemotingServer.start();
			
			int vipPort = port - 2;
			this.serverConfig.setListenPort(vipPort);
			this.nettyRemotingVipServer.start();
		}

	}
	
	public NettyRemotingClient getNettyRemotingClient() {
		return nettyRemotingClient;
	}

	public ProviderRegistryController getProviderController() {
		return providerController;
	}

	public String getRegistryAddress() {
		return registryAddress;
	}

	public String getServiceListenAddress() {
		return serviceListenAddress;
	}

	public FlowController getGlobalController() {
		return globalController;
	}

	public ProviderRPCController getProviderRPCController() {
		return providerRPCController;
	}

	public boolean isProviderStateIsHealthy() {
		return ProviderStateIsHealthy;
	}

	public void setProviderStateIsHealthy(boolean providerStateIsHealthy) {
		ProviderStateIsHealthy = providerStateIsHealthy;
	}
	
}
