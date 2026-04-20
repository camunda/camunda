/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import java.net.URI;
import java.util.*;
import junit.framework.AssertionFailedError;
import org.testcontainers.containers.Network;

public class TestContext<T extends TestContext<T>> {

  private String databaseType = null;
  private Network network;
  private String externalElsHost;
  private Integer externalElsPort;
  private String internalElsHost;
  private Integer internalElsPort;

  private URI zeebeGrpcAddress;
  private String internalZeebeContactPoint;

  private String indexPrefix;

  private String externalOperateHost;
  private Integer externalOperatePort;

  private final List<String> processesToAssert = new ArrayList<>();
  private Integer partitionCount;
  private Boolean multitenancyEnabled;
  private final Map<String, String> operateContainerEnvs = new LinkedHashMap<>();

  public String getDatabaseType() {
    return databaseType;
  }

  public T setDatabaseType(final String databaseType) {
    this.databaseType = databaseType;
    return (T) this;
  }

  public Network getNetwork() {
    return network;
  }

  public T setNetwork(final Network network) {
    this.network = network;
    return (T) this;
  }

  public String getExternalElsHost() {
    return externalElsHost;
  }

  public T setExternalElsHost(final String externalElsHost) {
    this.externalElsHost = externalElsHost;
    return (T) this;
  }

  public Integer getExternalElsPort() {
    return externalElsPort;
  }

  public T setExternalElsPort(final Integer externalElsPort) {
    this.externalElsPort = externalElsPort;
    return (T) this;
  }

  public String getInternalElsHost() {
    return internalElsHost;
  }

  public T setInternalElsHost(final String internalElsHost) {
    this.internalElsHost = internalElsHost;
    return (T) this;
  }

  public Integer getInternalElsPort() {
    return internalElsPort;
  }

  public T setInternalElsPort(final Integer internalElsPort) {
    this.internalElsPort = internalElsPort;
    return (T) this;
  }

  public URI getZeebeGrpcAddress() {
    return zeebeGrpcAddress;
  }

  public T setZeebeGrpcAddress(final URI grpcAddress) {
    zeebeGrpcAddress = grpcAddress;
    return (T) this;
  }

  public String getInternalZeebeContactPoint() {
    return internalZeebeContactPoint;
  }

  public T setInternalZeebeContactPoint(final String internalZeebeContactPoint) {
    this.internalZeebeContactPoint = internalZeebeContactPoint;
    return (T) this;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public T setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
    return (T) this;
  }

  public String getExternalOperateHost() {
    return externalOperateHost;
  }

  public T setExternalOperateHost(final String externalOperateHost) {
    this.externalOperateHost = externalOperateHost;
    return (T) this;
  }

  public Integer getExternalOperatePort() {
    return externalOperatePort;
  }

  public T setExternalOperatePort(final Integer externalOperatePort) {
    this.externalOperatePort = externalOperatePort;
    return (T) this;
  }

  public void addProcess(final String bpmnProcessId) {
    if (processesToAssert.contains(bpmnProcessId)) {
      throw new AssertionFailedError("Process was already created earlier: " + bpmnProcessId);
    }
    processesToAssert.add(bpmnProcessId);
  }

  public Integer getPartitionCount() {
    return partitionCount;
  }

  public TestContext<T> setPartitionCount(final Integer partitionCount) {
    this.partitionCount = partitionCount;
    return this;
  }

  public Boolean isMultitenancyEnabled() {
    return multitenancyEnabled;
  }

  public TestContext<T> setMultitenancyEnabled(final Boolean multitenancyEnabled) {
    this.multitenancyEnabled = multitenancyEnabled;
    return this;
  }

  public Map<String, String> getOperateContainerEnvs() {
    return operateContainerEnvs;
  }

  public void addOperateContainerEnv(final String key, final String value) {
    if (operateContainerEnvs.containsKey(key)) {
      throw new AssertionFailedError("Operate container env was already created earlier: " + key);
    }
    operateContainerEnvs.put(key, value);
  }
}
