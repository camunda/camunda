package org.camunda.optimize.dto.optimize.query.definition;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;

/**
 * This transfer object is used by REST Api and services only and is not persisted in
 * elastic search in it's aggregated way.
 *
 * @author Askar Akhmerov
 */
public class ExtendedProcessDefinitionOptimizeDto extends ProcessDefinitionOptimizeDto {

  protected String bpmn20Xml;

  public String getBpmn20Xml() {
    return bpmn20Xml;
  }

  public void setBpmn20Xml(String bpmn20Xml) {
    this.bpmn20Xml = bpmn20Xml;
  }
}
