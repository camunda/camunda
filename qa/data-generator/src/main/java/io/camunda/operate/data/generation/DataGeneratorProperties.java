/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import static io.camunda.operate.data.generation.DataGeneratorProperties.PROPERTIES_PREFIX;

@Component
@ConfigurationProperties(prefix=PROPERTIES_PREFIX)
public class DataGeneratorProperties {

  public static final String PROPERTIES_PREFIX = "camunda.operate.qa.data";

  private int processCount = 100;

  private int processInstanceCount = 10000;

  private int callActivityProcessInstanceCount = 100;

  private int incidentCount = 100;

  private int resolvedIncidentCount = 100;

  private String zeebeGatewayAddress = "localhost:26500";

  private String elasticsearchHost = "localhost";

  private int elasticsearchPort = 9200;

  private String zeebeElasticsearchPrefix = "zeebe-record";

  private int queueSize = 200;

  private int threadCount = 2;

  public int getProcessCount() {
    return processCount;
  }

  public void setProcessCount(int processCount) {
    this.processCount = processCount;
  }

  public int getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(int processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }

  public int getCallActivityProcessInstanceCount() {
    return callActivityProcessInstanceCount;
  }

  public DataGeneratorProperties setCallActivityProcessInstanceCount(
      final int callActivityProcessInstanceCount) {
    this.callActivityProcessInstanceCount = callActivityProcessInstanceCount;
    return this;
  }

  public int getIncidentCount() {
    return incidentCount;
  }

  public void setIncidentCount(int incidentCount) {
    this.incidentCount = incidentCount;
  }

  public int getResolvedIncidentCount() {
    return resolvedIncidentCount;
  }

  public DataGeneratorProperties setResolvedIncidentCount(final int resolvedIncidentCount) {
    this.resolvedIncidentCount = resolvedIncidentCount;
    return this;
  }

  public String getZeebeGatewayAddress() {
    return zeebeGatewayAddress;
  }

  public void setZeebeGatewayAddress(String gatewayAddress) {
    this.zeebeGatewayAddress = gatewayAddress;
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

  public int getThreadCount() {
    return threadCount;
  }

  public DataGeneratorProperties setThreadCount(final int threadCount) {
    this.threadCount = threadCount;
    return this;
  }
}
