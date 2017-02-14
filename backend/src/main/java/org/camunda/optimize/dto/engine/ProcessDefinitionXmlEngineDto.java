package org.camunda.optimize.dto.engine;

public class ProcessDefinitionXmlEngineDto implements EngineDto {

  private String id;
  private String bpmn20Xml;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBpmn20Xml() {
    return bpmn20Xml;
  }

  public void setBpmn20Xml(String bpmn20Xml) {
    this.bpmn20Xml = bpmn20Xml;
  }
}
