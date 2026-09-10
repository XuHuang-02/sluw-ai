package com.sinosig.sluw.application.commons.entity;


import com.sinosig.sluw.application.commons.utils.DateUtil;

import java.util.Date;

/**
 * 类名称：AssistantTrackEntity
 * 表名：AssistantTrack
 */
public class AssistantTrackEntity {

    /**
     * 字段名：id
     * 描述：序号
     */
    private Integer id;

    /**
     * 字段名：conversationId
     * 描述：会话号
     */
    private String conversationId;

    /**
     * 字段名：userCode
     * 描述：工号
     */
    private String userCode;

    /**
     * 字段名：userType
     * 描述：用户类型 E-内勤，S-外勤
     */
    private String userType;

    /**
     * 字段名：manageCom
     * 描述：机构代码
     */
    private String manageCom;

    /**
     * 字段名：question
     * 描述：问题描述
     */
    private String question;

    /**
     * 字段名：answer
     * 描述：回答结果
     */
    private String answer;

    /**
     * 字段名：mark
     * 描述：评分
     */
    private String mark;

    /**
     * 字段名：markDetail
     * 描述：评分详情
     */
    private String markDetail;

    /**
     * 字段名：massageId
     * 描述：消息号
     */
    private String massageId;

    /**
     * 字段名：duration
     * 描述：耗时
     */
    private Integer duration;

    /**
     * 字段名：totalPromptTokens
     * 描述：累计输入 Token 数
     */
    private Integer totalPromptTokens;

    /**
     * 字段名：totalCompletionTokens
     * 描述： 累计输出 Token 数
     */
    private Integer totalCompletionTokens;

    /**
     * 字段名：totalTokens
     * 描述：累计总 Token 数
     */
    private Integer totalTokens;

    /**
     * 字段名：makeDate
     * 描述：入机日期
     */
    private Date makeDate;
    /**
     * 字段名：makeTime
     * 描述：入机时间
     */
    private String makeTime;
    /**
     * 字段名：modifyDate
     * 描述：结束日期
     */
    private Date modifyDate;
    /**
     * 字段名：modifyTime
     * 描述：结束时间
     */
    private String modifyTime;
    /**
     * 字段名：remark1
     * 描述：备用1
     */
    private String remark1;
    /**
     * 字段名：remark2
     * 描述：备用2
     */
    private String remark2;
    /**
     * 字段名：remark3
     * 描述：备用3
     */
    private String remark3;
    /**
     * 字段名：remark4
     * 描述：备用4
     */
    private String remark4;
    /**
     * 字段名：remark5
     * 描述：备用5
     */
    private String remark5;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getManageCom() {
        return manageCom;
    }

    public void setManageCom(String manageCom) {
        this.manageCom = manageCom;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public String getMarkDetail() {
        return markDetail;
    }

    public void setMarkDetail(String markDetail) {
        this.markDetail = markDetail;
    }

    public String getMassageId() {
        return massageId;
    }

    public void setMassageId(String massageId) {
        this.massageId = massageId;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(Integer totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public Integer getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(Integer totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public void setMakeDate(String makeDate) {
        this.makeDate = DateUtil.parseDate(makeDate);
    }
    public String getMakeDateValue() {
        if (makeDate != null){
            return DateUtil.format(makeDate);
        }else{
            return null;
        }
    }
    public Date getMakeDate() {
        return makeDate;
    }
    public void setMakeDate(Date makeDate) {
        this.makeDate = makeDate;
    }

    public String getMakeTime() {
        return makeTime;
    }
    public void setMakeTime(String makeTime) {
        this.makeTime = makeTime;
    }

    public void setModifyDate(String modifyDate) {
        this.modifyDate = DateUtil.parseDate(modifyDate);
    }
    public String getModifyDateValue() {
        if (modifyDate != null){
            return DateUtil.format(modifyDate);
        }else{
            return null;
        }
    }
    public Date getModifyDate() {
        return modifyDate;
    }
    public void setModifyDate(Date modifyDate) {
        this.modifyDate = modifyDate;
    }

    public String getModifyTime() {
        return modifyTime;
    }
    public void setModifyTime(String modifyTime) {
        this.modifyTime = modifyTime;
    }

    public String getRemark1() {
        return remark1;
    }
    public void setRemark1(String remark1) {
        this.remark1 = remark1;
    }

    public String getRemark2() {
        return remark2;
    }
    public void setRemark2(String remark2) {
        this.remark2 = remark2;
    }

    public String getRemark3() {
        return remark3;
    }
    public void setRemark3(String remark3) {
        this.remark3 = remark3;
    }

    public String getRemark4() {
        return remark4;
    }
    public void setRemark4(String remark4) {
        this.remark4 = remark4;
    }

    public String getRemark5() {
        return remark5;
    }
    public void setRemark5(String remark5) {
        this.remark5 = remark5;
    }


}
