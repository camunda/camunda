package org.camunda.optimize.dto.optimize.query.analysis;

import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;

import java.util.ArrayList;
import java.util.List;

public class BranchAnalysisQueryDto {

  protected String end;
  protected String gateway;
  protected String processDefinitionKey;
  protected String processDefinitionVersion;
  protected List<FilterDto> filter = new ArrayList<>();

  public List<FilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<FilterDto> filter) {
    this.filter = filter;
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

  /**
   * The end event the branch analysis is referred to.
   */
  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  /**
   * The gateway the branch analysis is referred to.
   */
  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }
}
