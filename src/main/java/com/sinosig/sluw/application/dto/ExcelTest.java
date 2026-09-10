package com.sinosig.sluw.application.dto;

public class ExcelTest {
    // 假设表格有规则ID	规则类型名称	主题域	表名	表中文名	数据项代码	数据项名称	规则编码	规则说明	规则特征两列
    private String ruleID;
    private String ruleTypeName;
    private String Table;
    private String dataSingle;
    private String dataName;
    private String ruleCode;
    private String ruleDescription;
    private String ruleTZ;

    public String getRuleID() {
        return ruleID;
    }

    public void setRuleID(String ruleID) {
        this.ruleID = ruleID;
    }

    public String getRuleTypeName() {
        return ruleTypeName;
    }

    public void setRuleTypeName(String ruleTypeName) {
        this.ruleTypeName = ruleTypeName;
    }

    public String getTable() {
        return Table;
    }

    public void setTable(String table) {
        Table = table;
    }

    public String getDataSingle() {
        return dataSingle;
    }

    public void setDataSingle(String dataSingle) {
        this.dataSingle = dataSingle;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public void setRuleDescription(String ruleDescription) {
        this.ruleDescription = ruleDescription;
    }

    public String getRuleTZ() {
        return ruleTZ;
    }

    public void setRuleTZ(String ruleTZ) {
        this.ruleTZ = ruleTZ;
    }
}
