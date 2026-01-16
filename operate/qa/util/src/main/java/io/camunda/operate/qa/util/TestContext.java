/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import java.io.File;
import java.net.URI;
import java.util.*;
import junit.framework.AssertionFailedError;
import org.testcontainers.containers.Network;

public class TestContext<T extends TestContext<T>> {

  private String databaseType = null;
  private File zeebeDataFolder;
  private Network network;
  private String internalPostgresHost;
  private Integer internalPostgresPort;
  private String externalPostgresHost;
  private Integer externalPostgresPort;
  private Integer internalIdentityPort;
  private Integer externalIdentityPort;
  private String externalIdentityHost;
  private String internalIdentityHost;
  private String externalElsHost;
  private Integer externalElsPort;
  private String internalElsHost;
  private Integer internalElsPort;
  private String externalKeycloakHost;
  private Integer externalKeycloakPort;
  private String internalKeycloakHost;
  private Integer internalKeycloakPort;

  private URI zeebeGrpcAddress;
  private String internalZeebeContactPoint;

  private String zeebeIndexPrefix = "zeebe-record";
  private String indexPrefix;

  private String externalOperateHost;
  private Integer externalOperatePort;
  private String externalOperateContextPath = "/";

  private List<String> processesToAssert = new ArrayList<>();
  private Integer partitionCount;
  private Boolean multitenancyEnabled;
  private final Map<String, String> operateContainerEnvs = new LinkedHashMap<>();

  private String connectionType;

  public String getDatabaseType() {
    return databaseType;
  }

  public T setDatabaseType(final String databaseType) {
    this.databaseType = databaseType;
    return (T) this;
  }

  public File getZeebeDataFolder() {
    return zeebeDataFolder;
  }

  public T setZeebeDataFolder(final File zeebeDataFolder) {
    this.zeebeDataFolder = zeebeDataFolder;
    return (T) this;
  }

  public Network getNetwork() {
    return network;
  }

  public T setNetwork(final Network network) {
    this.network = network;
    return (T) this;
  }

  public String getInternalPostgresHost() {
    return internalPostgresHost;
  }

  public TestContext<T> setInternalPostgresHost(final String internalPostgresHost) {
    this.internalPostgresHost = internalPostgresHost;
    return this;
  }

  public Integer getInternalPostgresPort() {
    return internalPostgresPort;
  }

  public TestContext<T> setInternalPostgresPort(final Integer internalPostgresPort) {
    this.internalPostgresPort = internalPostgresPort;
    return this;
  }

  public String getExternalPostgresHost() {
    return externalPostgresHost;
  }

  public TestContext<T> setExternalPostgresHost(final String externalPostgresHost) {
    this.externalPostgresHost = externalPostgresHost;
    return this;
  }

  public Integer getExternalPostgresPort() {
    return externalPostgresPort;
  }

  public TestContext<T> setExternalPostgresPort(final Integer externalPostgresPort) {
    this.externalPostgresPort = externalPostgresPort;
    return this;
  }

  public Integer getInternalIdentityPort() {
    return internalIdentityPort;
  }

  public TestContext<T> setInternalIdentityPort(final Integer internalIdentityPort) {
    this.internalIdentityPort = internalIdentityPort;
    return this;
  }

  public Integer getExternalIdentityPort() {
    return externalIdentityPort;
  }

  public TestContext<T> setExternalIdentityPort(final Integer externalIdentityPort) {
    this.externalIdentityPort = externalIdentityPort;
    return this;
  }

  public String getExternalIdentityHost() {
    return externalIdentityHost;
  }

  public TestContext<T> setExternalIdentityHost(final String externalIdentityHost) {
    this.externalIdentityHost = externalIdentityHost;
    return this;
  }

  public String getInternalIdentityHost() {
    return internalIdentityHost;
  }

  public TestContext<T> setInternalIdentityHost(final String internalIdentityHost) {
    this.internalIdentityHost = internalIdentityHost;
    return this;
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

  public String getExternalKeycloakHost() {
    return externalKeycloakHost;
  }

  public TestContext<T> setExternalKeycloakHost(final String externalKeycloakHost) {
    this.externalKeycloakHost = externalKeycloakHost;
    return this;
  }

  public Integer getExternalKeycloakPort() {
    return externalKeycloakPort;
  }

  public TestContext<T> setExternalKeycloakPort(final Integer externalKeycloakPort) {
    this.externalKeycloakPort = externalKeycloakPort;
    return this;
  }

  public String getInternalKeycloakHost() {
    return internalKeycloakHost;
  }

  public TestContext<T> setInternalKeycloakHost(final String internalKeycloakHost) {
    this.internalKeycloakHost = internalKeycloakHost;
    return this;
  }

  public Integer getInternalKeycloakPort() {
    return internalKeycloakPort;
  }

  public TestContext<T> setInternalKeycloakPort(final Integer internalKeycloakPort) {
    this.internalKeycloakPort = internalKeycloakPort;
    return this;
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

  public String getZeebeIndexPrefix() {
    return zeebeIndexPrefix;
  }

  public T setZeebeIndexPrefix(final String zeebeIndexPrefix) {
    this.zeebeIndexPrefix = zeebeIndexPrefix;
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

  public String getExternalOperateContextPath() {
    return externalOperateContextPath;
  }

  public T setExternalOperateContextPath(final String externalOperateContextPath) {
    this.externalOperateContextPath = externalOperateContextPath;
    return (T) this;
  }

  public List<String> getProcessesToAssert() {
    return processesToAssert;
  }

  public T setProcessesToAssert(final List<String> processesToAssert) {
    this.processesToAssert = processesToAssert;
    return (T) this;
  }

  public void addProcess(final String bpmnProcessId) {
    if (processesToAssert.contains(bpmnProcessId)) {
      throw new AssertionFailedError("Process was already created earlier: " + bpmnProcessId);
    }
    processesToAssert.add(bpmnProcessId);
  }

  public String getInternalKeycloakBaseUrl() {
    return String.format("http://%s:%d", internalKeycloakHost, internalKeycloakPort);
  }

  public String getInternalIdentityBaseUrl() {
    return String.format("http://%s:%d", internalIdentityHost, internalIdentityPort);
  }

  public String getExternalKeycloakBaseUrl() {
    return String.format("http://%s:%d", externalKeycloakHost, externalKeycloakPort);
  }

  public String getExternalIdentityBaseUrl() {
    return String.format("http://%s:%d", externalIdentityHost, externalIdentityPort);
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

  public String getConnectionType() {
    return connectionType;
  }

  public TestContext<T> setConnectionType(final String connectionType) {
    this.connectionType = connectionType;
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
