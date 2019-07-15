/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import static org.camunda.operate.data.generation.DataGeneratorProperties.PROPERTIES_PREFIX;

@Component
@ConfigurationProperties(prefix=PROPERTIES_PREFIX)
public class DataGeneratorProperties {

  public static final String PROPERTIES_PREFIX = "camunda.operate.qa.data";

  private int workflowCount = 100;

  private int workflowInstanceCount = 10000;

  private int incidentCount = 100;

  private String zeebeBrokerContactPoint = "localhost:26500";

  private String elasticsearchHost = "localhost";

  private int elasticsearchPort = 9200;

  private String zeebeElasticsearchPrefix = "zeebe-record";

  private int queueSize = 50;

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

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }
}
