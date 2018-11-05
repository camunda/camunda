package org.camunda.optimize.dto.engine;

import java.io.Serializable;

public class DecisionDefinitionEngineDto implements Serializable,EngineDto {

  protected String id;
  protected String key;
  protected String category;
  protected String name;
  protected int version;
  protected String resource;
  protected String deploymentId;
  protected String tenantId;
  protected String decisionRequirementsDefinitionId;
  protected String decisionRequirementsDefinitionKey;
  protected Integer historyTimeToLive;
  protected String versionTag;

  public String getId() {
    return id;
  }

  public String getKey() {
    return key;
  }

  public String getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public int getVersion() {
    return version;
  }

  public String getResource() {
    return resource;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getDecisionRequirementsDefinitionId() {
    return decisionRequirementsDefinitionId;
  }

  public String getDecisionRequirementsDefinitionKey() {
    return decisionRequirementsDefinitionKey;
  }

  public Integer getHistoryTimeToLive() {
    return historyTimeToLive;
  }

  public String getVersionTag(){
    return versionTag;
  }

}