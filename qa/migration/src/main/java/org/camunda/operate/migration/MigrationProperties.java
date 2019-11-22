/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.camunda.operate.migration.MigrationProperties.PROPERTIES_PREFIX;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix=PROPERTIES_PREFIX)
public class MigrationProperties {

  public static final String PROPERTIES_PREFIX = "camunda.operate.migration";

  private int workflowCount = 10;

  private int workflowInstanceCount = 100;

  private int incidentCount = 50;

  private String zeebeBrokerContactPoint = "localhost:26500";

  private String elasticsearchHost = "localhost";

  private int elasticsearchPort = 9200;

  private String zeebeElasticsearchPrefix = "zeebe-record";
  
  private List<String> versions = Arrays.asList("1.0.0","1.1.0","1.2.0");
  
  public void setVersions(String versionsAsCSV) {
	  this.versions = Arrays.asList(versionsAsCSV.split("\\s*,\\s*"));
  }
  
  public List<String> getVersions(){
	  return versions;
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

  public String getZeebeElasticsearchPrefix() {
    return zeebeElasticsearchPrefix;
  }

  public void setZeebeElasticsearchPrefix(String zeebeElasticsearchPrefix) {
    this.zeebeElasticsearchPrefix = zeebeElasticsearchPrefix;
  }
}
