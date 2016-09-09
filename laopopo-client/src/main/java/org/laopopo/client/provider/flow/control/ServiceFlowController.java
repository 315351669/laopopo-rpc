package org.laopopo.client.provider.flow.control;

import java.util.concurrent.atomic.AtomicLong;

import org.laopopo.common.utils.SystemClock;

public class ServiceFlowController {

	private AtomicLong[] metricses = new AtomicLong[] { new AtomicLong(0), new AtomicLong(0), new AtomicLong(0) };

	public void incrementAtCurrentMinute() {

		long currentTime = SystemClock.millisClock().now();
		int index = (int) ((currentTime / 60000) % 3);

		AtomicLong atomicLong = metricses[index];
		atomicLong.incrementAndGet();

	}

	public long getLastCallCountAtLastMinute() {

		long currentTime = SystemClock.millisClock().now();
		int index = (int) (((currentTime / 60000) - 1) % 3);
		AtomicLong atomicLong = metricses[index];
		return atomicLong.get();

	}

	public long getCurrentCallCount() {

		long currentTime = SystemClock.millisClock().now();
		int index = (int) (((currentTime / 60000)) % 3);
		AtomicLong atomicLong = metricses[index];
		return atomicLong.get();

	}

	public long getNextMinuteCallCount() {

		long currentTime = SystemClock.millisClock().now();
		int index = (int) (((currentTime / 60000) + 1) % 3);
		AtomicLong atomicLong = metricses[index];
		return atomicLong.get();

	}

	public void clearNextMinuteCallCount() {

		long currentTime = SystemClock.millisClock().now();
		int index = (int) (((currentTime / 60000) + 1) % 3);
		AtomicLong atomicLong = metricses[index];
		atomicLong.set(0);
	}

	public AtomicLong[] getMetricses() {
		return metricses;
	}

	public void setMetricses(AtomicLong[] metricses) {
		this.metricses = metricses;
	}

}
