/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.camunda.operate.migration.MigrationProperties.PROPERTIES_PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix=PROPERTIES_PREFIX)
public class MigrationProperties {

  public static final String PROPERTIES_PREFIX = "camunda.operate.migration";

  private int workflowCount = 11;

  private int workflowInstanceCount = 101;

  private int incidentCount = 27;
  
  private int countOfResolveOperation = 3;
  
  private int countOfCancelOperation = 4;
  
  private String fromOperateBaseUrl = "http://localhost:8080";

  private String zeebeBrokerContactPoint = "localhost:26500";

  private String elasticsearchHost = "localhost";

  private int elasticsearchPort = 9200;

  private String archiverDateFormat = "yyyyMMdd";
  
  public void setFromOperateBaseUrl(String fromOperateBaseUrl) {
	  this.fromOperateBaseUrl = fromOperateBaseUrl;
  }
  
  public String getFromOperateBaseUrl() {
	  return fromOperateBaseUrl;
  }

  public int getWorkflowCount() {
    return workflowCount;
  }

  public void setWorkflowCount(int workflowCount) {
    this.workflowCount = workflowCount;
  }

  public int getWorkflowInstanceCount() {
    return workflowInstanceCount;
  }

  public void setWorkflowInstanceCount(int workflowInstanceCount) {
    this.workflowInstanceCount = workflowInstanceCount;
  }

  public int getIncidentCount() {
    return incidentCount;
  }

  public void setIncidentCount(int incidentCount) {
    this.incidentCount = incidentCount;
  }

  public String getZeebeBrokerContactPoint() {
    return zeebeBrokerContactPoint;
  }

  public void setZeebeBrokerContactPoint(String zeebeBrokerContactPoint) {
    this.zeebeBrokerContactPoint = zeebeBrokerContactPoint;
  }

  public String getElasticsearchHost() {
    return elasticsearchHost;
  }

  public void setElasticsearchHost(String elasticsearchHost) {
    this.elasticsearchHost = elasticsearchHost;
  }

  public int getElasticsearchPort() {
    return elasticsearchPort;
  }

  public void setElasticsearchPort(int elasticsearchPort) {
    this.elasticsearchPort = elasticsearchPort;
  }

  public int getCountOfResolveOperation() {
    return countOfResolveOperation;
  }

  public void setCountOfResolveOperation(int countOfResolveOperation) {
    this.countOfResolveOperation = countOfResolveOperation;
  }

  public int getCountOfCancelOperation() {
    return countOfCancelOperation;
  }

  public void setCountOfCancelOperation(int countOfCancelOperation) {
    this.countOfCancelOperation = countOfCancelOperation;
  }

  public String getArchiverDateFormat() {
    return archiverDateFormat;
  }

  public void setArchiverDateFormat(String archiverDateFormat) {
    this.archiverDateFormat = archiverDateFormat;
  }
}
