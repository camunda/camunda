package org.camunda.optimize.dto.engine;

public class DecisionDefinitionXmlEngineDto implements EngineDto {

  protected String id;
  protected String dmnXml;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDmnXml() {
    return dmnXml;
  }

  public void setDmnXml(String dmnXml) {
    this.dmnXml = dmnXml;
  }
}
