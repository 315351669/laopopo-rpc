var currPageIndex = 0;
var currLimit = 10;

$(function() {
	$("#monitorTable")
			.bootstrapTable(
					{

						url : "/laopopo-console/index.do",
						sortName : "rkey",// 排序列
						striped : true,// 條紋行
						sidePagination : "server",// 服务器分页
						clickToSelect : true,// 选择行即选择checkbox
						singleSelect : true,// 仅允许单选
						pagination : true,// 启用分页
						escape : true,// 过滤危险字符
						queryParams : getParams,// 携带参数
						pageCount : 10,// 每页行数
						pageIndex : 0,// 其实页
						method : "get",// 请求格式
						detailView : true,
						detailFormatter : detailFormatter,
						columns : [
								{
									field : 'serviceName',
									title : '服务名',
									align : 'center',
									width : '30',
									valign : 'middle',
								},
								{
									field : 'totalCallCount',
									title : '调用次数',
									align : 'center',
									width : '40',
									valign : 'middle',
								},
								{
									field : 'totalFailCount',
									title : '失败次数',
									align : 'center',
									width : '50',
									valign : 'bottom',
								},
								{
									field : 'loadBalanceStrategy',
									title : '负载策略',
									align : 'center',
									width : '50',
									valign : 'bottom',
									formatter : function(value, row, index) {
										switch (row.loadBalanceStrategy) {
										case "WEIGHTINGRANDOM":
											return "加权随机";
										case "RANDOM":
											return "随机";
										case "POLLING":
											return "轮询";

										}

									},
								},
								{
									field : 'totalHandlerRequestBodySize',
									title : '成功率',
									align : 'center',
									width : '150',
									valign : 'bottom',
									formatter : function(value, row, index) {
										var callSuccessRatio = "";
										if((row.totalCallCount + row.totalFailCount) != 0){
											callSuccessRatio= (row.totalCallCount * 100  / (row.totalCallCount + row.totalFailCount)).toString().substring(0,4) +"%";
										}else{
											callSuccessRatio = "-";
										}
										return callSuccessRatio;
									},
								},
								{
									field : 'id',
									title : '用户操作',
									align : 'center',
									width : '290px',
									formatter : function(value, row, index) {
										var btnStr = "&nbsp;&nbsp;&nbsp;&nbsp;<button onclick=suitBookingCitys() class='btn btn-success btn-xs'><i class='fa fa-edit'></i><span >刷新</span></button>";
										btnStr += "&nbsp;&nbsp;&nbsp;&nbsp;<button onclick=editRegional()  class='btn btn-warning btn-xs'><i class='fa fa-eye'></i><span >负载策略</span></button>";
										return btnStr;
									}
								} ],
						onPageChange : function(number, size) {
							currPageIndex = number;
							currLimit = size
						},
						onLoadSuccess : function() {
							$("#searchBtn").button('reset');
						}
					});
});

function detailFormatter(index, row) {
	var html = '';
	html += '<table class="table"><caption>服务提供者信息</caption><thead><tr>'
			+'<th style="text-align: center; vertical-align: middle;">服务提供地址</th>'
			+'<th style="text-align: center; vertical-align: middle;">权重</th>'
			+'<th style="text-align: center; vertical-align: middle;">是否可降级</th>'
			+'<th style="text-align: center; vertical-align: middle;">是否已经降级</th>'
			+'<th style="text-align: center; vertical-align: middle;">审核状态</th>'
			+'<th style="text-align: center; vertical-align: middle;">调用次数</th>'
			+'<th style="text-align: center; vertical-align: middle;">失败次数</th>'
			+'<th style="text-align: center; vertical-align: middle;">成功率</th>'
			+'<th style="text-align: center; vertical-align: middle;">操作</th>'
			+'</tr></thead><tbody>';
	$.each(row, function(key, value) {
		if(key == "providerMaps"){
			
			for(var obj in value){
				var reviewStr = '';
				switch (value[obj].serviceReviewState) {
				case 'PASS_REVIEW':
					reviewStr = "审核通过";
					break;
				case 'HAS_NOT_REVIEWED':
					reviewStr = "未审核";
					break;
				case 'NOT_PASS_REVIEW':
					reviewStr = "未通过审核";
					break;
				case 'FORBIDDEN':
					reviewStr = "禁用";
					break;
				default:
					break;
				}
				var isSupportDegrade = value[obj].isSupportDegrade ? '是':'否';
				var isDegradeService = value[obj].isDegradeService ? '是':'否';
				var callSuccessRatio = "";
				if(value[obj].callCount + value[obj].failCount != 0){
					callSuccessRatio= (value[obj].callCount * 100  / (value[obj].callCount + value[obj].failCount)).toString().substring(0,4) +"%";
				}else{
					callSuccessRatio = "-";
				}
				html += '<tr><td>'+value[obj].host+':'+value[obj].port+'</td>'
						+'<td>'+50+'</td>'
						+'<td>'+ isSupportDegrade +'</td>'
						+'<td>'+ isDegradeService +'</td>'
						+'<td>'+reviewStr+'</td>'
						+'<td>'+value[obj].callCount+'</td>'
						+'<td>'+value[obj].failCount+'</td>'
						+'<td>'+callSuccessRatio+'</td>'
						+'<td><button class="btn btn-success btn-xs" onclick=forbidden("'+value[obj].host+'",'+value[obj].port+',"'+row.serviceName+'")>禁用</button>'
						+'&nbsp;&nbsp;<button class="btn btn-success btn-xs">降级</button>'
						+'&nbsp;&nbsp;<button class="btn btn-success btn-xs">审核通过</button></td></tr>';
			}
		}
	});
	html += "</tbody></table>";
	
	html += '<table class="table"><caption>服务消费者信息</caption><thead><tr>'
		+'<th style="text-align: center; vertical-align: middle;">服务提供地址</th>'
		+'</tr></thead><tbody>';
	$.each(row, function(key, value) {
		if(key == "consumerInfos"){
			
			if(value.length == 0){
				html += '<tr><td>暂无消费者</td></tr>';
			}
			
			for(var index = 0;index < value.length;index++){ 
				html += '<tr><td>'+value[index].host +":"+value[index].port+'</td></tr>';
			}
		}
	});
	html += "</tbody></table>";
	return html;
}

function forbidden(host,port,serviceName){
	$.ajax({
        url: "/laopopo-console/manager.do",
        type: 'GET',
        data: {
        	"managerType" : 1,
        	"host":host,
        	"port":port,
        	"serviceName":serviceName
        },
        async: false,
        success: function( result ) {
        	 
		}
	});
        
}

// 默认加载时携带参数
function getParams(params) {
	var searchKey = $("#searchKey").val();
	return {
		bysex : 1,
		limit : params.limit,
		offset : params.offset,
		search : searchKey
	};
}