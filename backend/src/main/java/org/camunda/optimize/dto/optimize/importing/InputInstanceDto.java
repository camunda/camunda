package org.camunda.optimize.dto.optimize.importing;

public class InputInstanceDto {
  private String id;
  private String clauseId;
  private String clauseName;
  private String type;
  private String value;

  protected InputInstanceDto() {
  }

  public InputInstanceDto(final String id, final String clauseId, final String clauseName, final String type,
                          final String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.type = type;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getClauseId() {
    return clauseId;
  }

  public void setClauseId(final String clauseId) {
    this.clauseId = clauseId;
  }

  public String getClauseName() {
    return clauseName;
  }

  public void setClauseName(final String clauseName) {
    this.clauseName = clauseName;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
