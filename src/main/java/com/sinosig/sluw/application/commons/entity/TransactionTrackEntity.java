package com.sinosig.sluw.application.commons.entity;

import com.sinosig.sluw.application.commons.utils.DateUtil;

import java.util.Date;


/**
 * 类名称：TransactionTrackEntity
 * 交易轨迹表
 * 创建时间：2018-10-10
 */
public class TransactionTrackEntity {

    /**
     * 银行交易跟踪号
     */
    private String trackNo;
    /**
     * 银行交易流水号
     */
    private String transNo;
    /**
     * 银行代码
     */
    private String bankCode;
    /**
     * 银行地区代码
     */
    private String zoneNo;
    /**
     * 银行网点代码
     */
    private String bankNode;
    /**
     * 关联/上笔流水号
     */
    private String relativeTranNo;
    /**
     * 交易代码
     */
    private String transactionCode;
    /**
     * 销售渠道 00-银保通柜面；01-银保通网销
     */
    private String salesType;
    /**
     * 销售方式：0000-银保通柜面；0101-银保通网销-网银端；0102-银保通网销-手机端；0103-银保通网销-互联网；0108-银保通网销-终端；0109-银保通网销-银行电销；
     */
    private String salesMode;
    /**
     * 交易发起日期
     */
    private Date transDate;
    /**
     * 交易发起时间
     */
    private String transTime;
    /**
     * 交易金额
     */
    private double transAmnt;
    /**
     * 是否已对账
     */
    private String isBala;
    /**
     * 投保单号
     */
    private String proposalNo;
    /**
     * 保单号
     */
    private String contNo;
    /**
     * 交易状态：1-请求；2-结束
     */
    private String status;
    /**
     * 银行返回码；1-返回交易成功；0-交易失败
     */
    private String rCode;
    /**
     * 返回给银行的错误信息
     */
    private String descr;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 入机日期
     */
    private Date makeDate;
    /**
     * 入机时间
     */
    private String makeTime;
    /**
     * 最后修改日期
     */
    private Date modifyDate;
    /**
     * 最后修改时间
     */
    private String modifyTime;
    /**
     * 备用字段1 业务归属日期
     */
    private String standBy1;
    /**
     * 备用字段2  交易轨迹表同步标识
     */
    private String standBy2;
    /**
     * 备用字段3 客户经理姓名
     */
    private String standBy3;
    /**
     * 备用字段4  执业证编号
     */
    private String standBy4;
    /**
     * 备用字段5  银行关联投保单号
     */
    private String standBy5;
    /**
     * 备用字段6
     */
    private String standBy6;
    /**
     * 备用字段7
     */
    private String standBy7;
    /**
     * 备用字段8
     */
    private String standBy8;
    /**
     * 备用字段9
     */
    private String standBy9;
    /**
     * 备用字段10
     */
    private String standBy10;
    /**
     * 备用字段11  银行端的销售渠道编码
     */
    private String standBy11;
    /**
     * 备用字段12
     */
    private String standBy12;

    public String getTrackNo() {
        return trackNo;
    }

    public void setTrackNo(String trackNo) {
        this.trackNo = trackNo;
    }

    public String getTransNo() {
        return transNo;
    }

    public void setTransNo(String transNo) {
        this.transNo = transNo;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public String getBankNode() {
        return bankNode;
    }

    public void setBankNode(String bankNode) {
        this.bankNode = bankNode;
    }

    public String getRelativeTranNo() {
        return relativeTranNo;
    }

    public void setRelativeTranNo(String relativeTranNo) {
        this.relativeTranNo = relativeTranNo;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public String getSalesType() {
        return salesType;
    }

    public void setSalesType(String salesType) {
        this.salesType = salesType;
    }

    public String getSalesMode() {
        return salesMode;
    }

    public void setSalesMode(String salesMode) {
        this.salesMode = salesMode;
    }

    public void setTransDate(String transDate) {
        this.transDate = DateUtil.parseDate(transDate);
    }

    public String getTransDateValue() {
        if (transDate != null) {
            return DateUtil.format(transDate);
        } else {
            return null;
        }
    }

    public Date getTransDate() {
        return transDate;
    }

    public void setTransDate(Date transDate) {
        this.transDate = transDate;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public double getTransAmnt() {
        return transAmnt;
    }

    public void setTransAmnt(double transAmnt) {
        this.transAmnt = transAmnt;
    }

    public String getIsBala() {
        return isBala;
    }

    public void setIsBala(String isBala) {
        this.isBala = isBala;
    }

    public String getProposalNo() {
        return proposalNo;
    }

    public void setProposalNo(String proposalNo) {
        this.proposalNo = proposalNo;
    }

    public String getContNo() {
        return contNo;
    }

    public void setContNo(String contNo) {
        this.contNo = contNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRCode() {
        return rCode;
    }

    public void setRCode(String rCode) {
        this.rCode = rCode;
    }

    public String getDescr() {
        return descr;
    }

    public void setDescr(String descr) {
        this.descr = descr;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setMakeDate(String makeDate) {
        this.makeDate = DateUtil.parseDate(makeDate);
    }

    public String getMakeDateValue() {
        if (makeDate != null) {
            return DateUtil.format(makeDate);
        } else {
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
        if (modifyDate != null) {
            return DateUtil.format(modifyDate);
        } else {
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

    public String getStandBy1() {
        return standBy1;
    }

    public void setStandBy1(String standBy1) {
        this.standBy1 = standBy1;
    }

    public String getStandBy2() {
        return standBy2;
    }

    public void setStandBy2(String standBy2) {
        this.standBy2 = standBy2;
    }

    public String getStandBy3() {
        return standBy3;
    }

    public void setStandBy3(String standBy3) {
        this.standBy3 = standBy3;
    }

    public String getStandBy4() {
        return standBy4;
    }

    public void setStandBy4(String standBy4) {
        this.standBy4 = standBy4;
    }

    public String getStandBy5() {
        return standBy5;
    }

    public void setStandBy5(String standBy5) {
        this.standBy5 = standBy5;
    }

    public String getStandBy6() {
        return standBy6;
    }

    public void setStandBy6(String standBy6) {
        this.standBy6 = standBy6;
    }

    public String getStandBy7() {
        return standBy7;
    }

    public void setStandBy7(String standBy7) {
        this.standBy7 = standBy7;
    }

    public String getStandBy8() {
        return standBy8;
    }

    public void setStandBy8(String standBy8) {
        this.standBy8 = standBy8;
    }

    public String getStandBy9() {
        return standBy9;
    }

    public void setStandBy9(String standBy9) {
        this.standBy9 = standBy9;
    }

    public String getStandBy10() {
        return standBy10;
    }

    public void setStandBy10(String standBy10) {
        this.standBy10 = standBy10;
    }

    public String getStandBy11() {
        return standBy11;
    }

    public void setStandBy11(String standBy11) {
        this.standBy11 = standBy11;
    }

    public String getStandBy12() {
        return standBy12;
    }

    public void setStandBy12(String standBy12) {
        this.standBy12 = standBy12;
    }


}
