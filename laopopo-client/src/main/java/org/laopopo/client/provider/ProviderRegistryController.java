package org.laopopo.client.provider;

import io.netty.channel.Channel;

import org.laopopo.remoting.model.RemotingTransporter;


/**
 * 
 * @author BazingaLyn
 * @description provider端的控制器
 * @time 2016年8月16日
 * @modifytime
 */
public class ProviderRegistryController {
	
	private DefaultProvider defaultProvider;
	
	//provider与注册中心的所有逻辑控制器
	private RegistryController registryController;
	
	//provider与monitor端通信的控制器
	private ProviderMonitorController providerMonitorController;
	
	//本地服务编织服务管理
	private LocalServerWrapperManager localServerWrapperManager;
	
	private final ServiceProviderContainer providerContainer;
	
	public ProviderRegistryController(DefaultProvider defaultProvider) {
		this.defaultProvider = defaultProvider;
		providerContainer = new DefaultServiceProviderContainer();
		localServerWrapperManager = new LocalServerWrapperManager(defaultProvider);
		registryController = new RegistryController(defaultProvider);
		providerMonitorController = new ProviderMonitorController(defaultProvider);
	}

	public DefaultProvider getDefaultProvider() {
		return defaultProvider;
	}

	public void setDefaultProvider(DefaultProvider defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	public LocalServerWrapperManager getLocalServerWrapperManager() {
		return localServerWrapperManager;
	}

	public void setLocalServerWrapperManager(LocalServerWrapperManager localServerWrapperManager) {
		this.localServerWrapperManager = localServerWrapperManager;
	}

	public RegistryController getRegistryController() {
		return registryController;
	}

	public void setRegistryController(RegistryController registryController) {
		this.registryController = registryController;
	}

	public ProviderMonitorController getProviderMonitorController() {
		return providerMonitorController;
	}

	public void setProviderMonitorController(ProviderMonitorController providerMonitorController) {
		this.providerMonitorController = providerMonitorController;
	}

	public ServiceProviderContainer getProviderContainer() {
		return providerContainer;
	}

	public RemotingTransporter handlerServiceDegrade(RemotingTransporter request, Channel channel) {
		return null;
	}
	
	
	

}
