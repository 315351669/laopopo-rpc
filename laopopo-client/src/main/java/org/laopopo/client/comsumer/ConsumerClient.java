package org.laopopo.client.comsumer;

import org.laopopo.remoting.netty.NettyClientConfig;

public class ConsumerClient extends DefaultConsumer {

	public ConsumerClient(NettyClientConfig registryClientConfig, NettyClientConfig providerClientConfig) {
		super(registryClientConfig, providerClientConfig);
	}

	@Override
	public Object call(String serviceName, Object... args) throws Throwable {
		return null;
	}

	@Override
	public Object call(SubcribeService subcribeService, Object... args) throws Throwable {
		return null;
	}

	@Override
	public Object call(SubcribeService subcribeService, long timeout, Object... args) throws Throwable {
		return null;
	}

	@Override
	public Object call(String serviceName, long timeout, Object... args) throws Throwable {
		return null;
	}

}
