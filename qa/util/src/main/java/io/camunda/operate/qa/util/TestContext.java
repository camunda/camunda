/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.testcontainers.containers.Network;

public class TestContext<T extends TestContext<T>> {

  private File zeebeDataFolder;
  private Network network;
  private String externalElsHost;
  private Integer externalElsPort;
  private String internalElsHost;
  private Integer internalElsPort;

  private String externalZeebeContactPoint;
  private String internalZeebeContactPoint;

  private String zeebeIndexPrefix = "zeebe-record";

  private String externalOperateHost;
  private Integer externalOperatePort;
  private String externalOperateContextPath = "/";

  private List<String> processesToAssert = new ArrayList<>();

  public File getZeebeDataFolder() {
    return zeebeDataFolder;
  }

  public T setZeebeDataFolder(File zeebeDataFolder) {
    this.zeebeDataFolder = zeebeDataFolder;
    return (T)this;
  }

  public Network getNetwork() {
    return network;
  }

  public T setNetwork(Network network) {
    this.network = network;
    return (T)this;
  }

  public String getExternalElsHost() {
    return externalElsHost;
  }

  public T setExternalElsHost(String externalElsHost) {
    this.externalElsHost = externalElsHost;
    return (T)this;
  }

  public Integer getExternalElsPort() {
    return externalElsPort;
  }

  public T setExternalElsPort(Integer externalElsPort) {
    this.externalElsPort = externalElsPort;
    return (T)this;
  }

  public String getInternalElsHost() {
    return internalElsHost;
  }

  public T setInternalElsHost(String internalElsHost) {
    this.internalElsHost = internalElsHost;
    return (T)this;
  }

  public Integer getInternalElsPort() {
    return internalElsPort;
  }

  public T setInternalElsPort(Integer internalElsPort) {
    this.internalElsPort = internalElsPort;
    return (T)this;
  }

  public String getExternalZeebeContactPoint() {
    return externalZeebeContactPoint;
  }

  public T setExternalZeebeContactPoint(String externalZeebeContactPoint) {
    this.externalZeebeContactPoint = externalZeebeContactPoint;
    return (T)this;
  }

  public String getInternalZeebeContactPoint() {
    return internalZeebeContactPoint;
  }

  public T setInternalZeebeContactPoint(String internalZeebeContactPoint) {
    this.internalZeebeContactPoint = internalZeebeContactPoint;
    return (T)this;
  }

  public String getZeebeIndexPrefix() {
    return zeebeIndexPrefix;
  }

  public T setZeebeIndexPrefix(String zeebeIndexPrefix) {
    this.zeebeIndexPrefix = zeebeIndexPrefix;
    return (T)this;
  }

  public String getExternalOperateHost() {
    return externalOperateHost;
  }

  public T setExternalOperateHost(String externalOperateHost) {
    this.externalOperateHost = externalOperateHost;
    return (T)this;
  }

  public Integer getExternalOperatePort() {
    return externalOperatePort;
  }

  public T setExternalOperatePort(Integer externalOperatePort) {
    this.externalOperatePort = externalOperatePort;
    return (T)this;
  }

  public String getExternalOperateContextPath() {
    return externalOperateContextPath;
  }

  public T setExternalOperateContextPath(String externalOperateContextPath) {
    this.externalOperateContextPath = externalOperateContextPath;
    return (T)this;
  }

  public List<String> getProcessesToAssert() {
    return processesToAssert;
  }

  public T setProcessesToAssert(List<String> processesToAssert) {
    this.processesToAssert = processesToAssert;
    return (T)this;
  }

  public void addProcess(String bpmnProcessId) {
    if (processesToAssert.contains(bpmnProcessId)) {
      throw new AssertionFailedError("Process was already created earlier: " + bpmnProcessId);
    }
    processesToAssert.add(bpmnProcessId);
  }
}
