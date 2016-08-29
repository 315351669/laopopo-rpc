package org.laopopo.common.utils;




public class Constants {
	
	public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
	
	public static final int READER_IDLE_TIME_SECONDS = 60;
	
	public static final int WRITER_IDLE_TIME_SECONDS = 30;
	
	public static final int DEFAULT_WEIGHT = 50;
	
	public static final int DEFAULT_CONNECTION_COUNT = 1;
	
	
	public static final String ACK_PUBLISH_SUCCESS = "发布服务成功";
	
	public static final String ACK_PUBLISH_FAILURE = "发布服务失败";
	
	public static final String ACK_SUBCRIBE_SERVICE_SUCCESS = "订阅服务成功";
	
	public static final String ACK_SUBCRIBE_SERVICE_FAILED = "订阅服务失败";
	
	public static final String ACK_SUBCRIBE_SERVICE_CANCEL_SUCCESS = "取消订阅服务成功";
	
	public static final String ACK_SUBCRIBE_SERVICE_CANCEL_FAIL = "取消订阅服务失败";
	
	public static final String ACK_PUBLISH_CANCEL_SUCCESS = "取消发布服务成功";
	
	public static final String ACK_PUBLISH_CANCEL_FAILURE = "取消发布服务失败";
	
	public static final String ACK_OPERATION_SUCCESS = "操作成功";
	
	public static final String ACK_OPERATION_FAILURE = "操作失败";
	
}
