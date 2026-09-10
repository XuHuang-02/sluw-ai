//package com.sinosig.sluw.application.tools;
//
//import node.com.sinosig.sluw.application.IntentAnalyzerNode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//
//public class DefaultTools {
//    private static final Logger logger = LoggerFactory.getLogger(DefaultTools.class);
//    @Tool(description = "通过客户证件号和银行代码获取投保时提交的反洗钱数据。")
//    public String getAntiMoneyLaundering(@ToolParam(description = "证件号") String idNo, @ToolParam(description = "银行代码") String bankCode){
//        logger.info("证件号：{} ,银行代码：{}",idNo,bankCode);
//        return "{\"value\":\"银行提交的证件有效期为2028-1-10 --- "+"提交的反洗钱证件影像有效期为206-8-16。\"}";
//    }
//    @Tool(description = "通过客户证件号获取投保时的落地机构与地址。")
//    public String getManagecomAndAddress(@ToolParam(description = "证件号") String idNo){
//        logger.info("证件号：{} ",idNo);
//        return "{\"value\":\"银行提交的落地机构代码为86010102 --- "+"地址为河北省沧州市。\"}";
//    }
//}
