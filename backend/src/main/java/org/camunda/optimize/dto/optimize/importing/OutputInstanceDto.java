package org.camunda.optimize.dto.optimize.importing;

public class OutputInstanceDto {
  private String id;
  private String clauseId;
  private String clauseName;
  private String ruleId;
  private Integer ruleOrder;
  private String variableName;
  private String type;
  private String value;

  protected OutputInstanceDto() {
  }

  public OutputInstanceDto(final String id, final String clauseId, final String clauseName, final String ruleId,
                           final Integer ruleOrder, final String variableName, final String type, final String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.ruleId = ruleId;
    this.ruleOrder = ruleOrder;
    this.variableName = variableName;
    this.type = type;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public String getClauseId() {
    return clauseId;
  }

  public String getClauseName() {
    return clauseName;
  }

  public String getRuleId() {
    return ruleId;
  }

  public Integer getRuleOrder() {
    return ruleOrder;
  }

  public String getVariableName() {
    return variableName;
  }

  public String getType() {
    return type;
  }

  public String getValue() {
    return value;
  }
}
