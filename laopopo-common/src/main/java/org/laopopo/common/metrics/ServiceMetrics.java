package org.laopopo.common.metrics;

import java.util.List;

import org.laopopo.common.rpc.ServiceReviewState;

/**
 * 
 * @author BazingaLyn
 * @description 
 * @time
 * @modifytime
 */
public class ServiceMetrics {
	
	private String serviceName;                      //服务名
	private Long totalCallCount;                     //该服务的总共的统计次数
	private Long totalFailCount;                     //该服务的总共的失败次数
	private Double totalHandlerRequestBodySize;      //该服务的请求的总大小
	private List<ConsumerInfo> consumerInfos;        //该服务的消费者的信息
	private List<ProviderInfo> providerInfos;        //该服务的提供者的信息
	
	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public Long getTotalCallCount() {
		return totalCallCount;
	}

	public void setTotalCallCount(Long totalCallCount) {
		this.totalCallCount = totalCallCount;
	}

	public Long getTotalFailCount() {
		return totalFailCount;
	}

	public void setTotalFailCount(Long totalFailCount) {
		this.totalFailCount = totalFailCount;
	}

	public Double getTotalHandlerRequestBodySize() {
		return totalHandlerRequestBodySize;
	}

	public void setTotalHandlerRequestBodySize(Double totalHandlerRequestBodySize) {
		this.totalHandlerRequestBodySize = totalHandlerRequestBodySize;
	}

	public List<ConsumerInfo> getConsumerInfos() {
		return consumerInfos;
	}

	public void setConsumerInfos(List<ConsumerInfo> consumerInfos) {
		this.consumerInfos = consumerInfos;
	}

	public List<ProviderInfo> getProviderInfos() {
		return providerInfos;
	}

	public void setProviderInfos(List<ProviderInfo> providerInfos) {
		this.providerInfos = providerInfos;
	}

	public static class ConsumerInfo {
		
		private int port;
		
		private String host;

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}
		
	}
	
	public static class ProviderInfo {
		
		private int port;                  //端口号
		private String host; 			   //host
		private Long callCount;            //调用的次数
		private Long failCount;            //失败的次数
		private Double handlerAvgTime;     //处理的平均时间
		private Double handlerDataAvgSize; //处理请求数据包的平均大小
		private Boolean isDegradeService;  //是否已经降级
		private Boolean isSupportDegrade;  //是否支持降级
		private Boolean isVipService;      //是否是VIP服务
		private ServiceReviewState serviceReviewState;   //服务的审核状态
		
		public int getPort() {
			return port;
		}
		public void setPort(int port) {
			this.port = port;
		}
		public String getHost() {
			return host;
		}
		public void setHost(String host) {
			this.host = host;
		}
		public Long getCallCount() {
			return callCount;
		}
		public void setCallCount(Long callCount) {
			this.callCount = callCount;
		}
		public Long getFailCount() {
			return failCount;
		}
		public void setFailCount(Long failCount) {
			this.failCount = failCount;
		}
		public Double getHandlerAvgTime() {
			return handlerAvgTime;
		}
		public void setHandlerAvgTime(Double handlerAvgTime) {
			this.handlerAvgTime = handlerAvgTime;
		}
		public Double getHandlerDataAvgSize() {
			return handlerDataAvgSize;
		}
		public void setHandlerDataAvgSize(Double handlerDataAvgSize) {
			this.handlerDataAvgSize = handlerDataAvgSize;
		}
		public Boolean getIsDegradeService() {
			return isDegradeService;
		}
		public void setIsDegradeService(Boolean isDegradeService) {
			this.isDegradeService = isDegradeService;
		}
		public Boolean getIsSupportDegrade() {
			return isSupportDegrade;
		}
		public void setIsSupportDegrade(Boolean isSupportDegrade) {
			this.isSupportDegrade = isSupportDegrade;
		}
		public Boolean getIsVipService() {
			return isVipService;
		}
		public void setIsVipService(Boolean isVipService) {
			this.isVipService = isVipService;
		}
		public ServiceReviewState getServiceReviewState() {
			return serviceReviewState;
		}
		public void setServiceReviewState(ServiceReviewState serviceReviewState) {
			this.serviceReviewState = serviceReviewState;
		}
	}

}
