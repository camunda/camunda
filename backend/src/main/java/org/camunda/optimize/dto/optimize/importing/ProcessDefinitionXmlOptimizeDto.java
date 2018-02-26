package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.HashMap;
import java.util.Map;

public class ProcessDefinitionXmlOptimizeDto implements OptimizeDto {

  protected String processDefinitionId;
  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected String bpmn20Xml;
  protected String engine;
  protected Map<String, String> flowNodeNames = new HashMap<>();

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public void setFlowNodeNames(Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getBpmn20Xml() {
    return bpmn20Xml;
  }

  public void setBpmn20Xml(String bpmn20Xml) {
    this.bpmn20Xml = bpmn20Xml;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }
}
