/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data.generation;

import static io.camunda.operate.data.generation.DataGeneratorProperties.PROPERTIES_PREFIX;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = PROPERTIES_PREFIX)
public class DataGeneratorProperties {

  public static final String PROPERTIES_PREFIX = "camunda.operate.qa.data";

  private int processCount = 100;

  private int processInstanceCount = 10000;

  private int callActivityProcessInstanceCount = 100;

  private int incidentCount = 100;

  private int resolvedIncidentCount = 100;

  private URI zeebeGrpcAddress = URI.create("http://localhost:26500");

  private String elasticsearchHost = "localhost";

  private int elasticsearchPort = 9200;

  private String zeebeElasticsearchPrefix = "zeebe-record";

  private int queueSize = 200;

  private int threadCount = 2;

  public int getProcessCount() {
    return processCount;
  }

  public void setProcessCount(final int processCount) {
    this.processCount = processCount;
  }

  public int getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(final int processInstanceCount) {
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

  public void setIncidentCount(final int incidentCount) {
    this.incidentCount = incidentCount;
  }

  public int getResolvedIncidentCount() {
    return resolvedIncidentCount;
  }

  public DataGeneratorProperties setResolvedIncidentCount(final int resolvedIncidentCount) {
    this.resolvedIncidentCount = resolvedIncidentCount;
    return this;
  }

  public URI getZeebeGrpcAddress() {
    return zeebeGrpcAddress;
  }

  public void setZeebeGrpcAddress(final URI gatewayAddress) {
    zeebeGrpcAddress = gatewayAddress;
  }

  public String getElasticsearchHost() {
    return elasticsearchHost;
  }

  public void setElasticsearchHost(final String elasticsearchHost) {
    this.elasticsearchHost = elasticsearchHost;
  }

  public int getElasticsearchPort() {
    return elasticsearchPort;
  }

  public void setElasticsearchPort(final int elasticsearchPort) {
    this.elasticsearchPort = elasticsearchPort;
  }

  public String getZeebeElasticsearchPrefix() {
    return zeebeElasticsearchPrefix;
  }

  public void setZeebeElasticsearchPrefix(final String zeebeElasticsearchPrefix) {
    this.zeebeElasticsearchPrefix = zeebeElasticsearchPrefix;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(final int queueSize) {
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
