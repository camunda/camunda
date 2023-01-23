/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util;

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

  private String zeebeIndexPrefix;

  private String externalTasklistHost;
  private Integer externalTasklistPort;
  private String externalTasklistContextPath = "/";

  private List<String> processesToAssert = new ArrayList<>();

  public File getZeebeDataFolder() {
    return zeebeDataFolder;
  }

  public T setZeebeDataFolder(File zeebeDataFolder) {
    this.zeebeDataFolder = zeebeDataFolder;
    return (T) this;
  }

  public Network getNetwork() {
    return network;
  }

  public T setNetwork(Network network) {
    this.network = network;
    return (T) this;
  }

  public String getExternalElsHost() {
    return externalElsHost;
  }

  public T setExternalElsHost(String externalElsHost) {
    this.externalElsHost = externalElsHost;
    return (T) this;
  }

  public Integer getExternalElsPort() {
    return externalElsPort;
  }

  public T setExternalElsPort(Integer externalElsPort) {
    this.externalElsPort = externalElsPort;
    return (T) this;
  }

  public String getInternalElsHost() {
    return internalElsHost;
  }

  public T setInternalElsHost(String internalElsHost) {
    this.internalElsHost = internalElsHost;
    return (T) this;
  }

  public Integer getInternalElsPort() {
    return internalElsPort;
  }

  public T setInternalElsPort(Integer internalElsPort) {
    this.internalElsPort = internalElsPort;
    return (T) this;
  }

  public String getExternalZeebeContactPoint() {
    return externalZeebeContactPoint;
  }

  public T setExternalZeebeContactPoint(String externalZeebeContactPoint) {
    this.externalZeebeContactPoint = externalZeebeContactPoint;
    return (T) this;
  }

  public String getInternalZeebeContactPoint() {
    return internalZeebeContactPoint;
  }

  public T setInternalZeebeContactPoint(String internalZeebeContactPoint) {
    this.internalZeebeContactPoint = internalZeebeContactPoint;
    return (T) this;
  }

  public String getZeebeIndexPrefix() {
    return zeebeIndexPrefix;
  }

  public T setZeebeIndexPrefix(String zeebeIndexPrefix) {
    this.zeebeIndexPrefix = zeebeIndexPrefix;
    return (T) this;
  }

  public String getExternalTasklistHost() {
    return externalTasklistHost;
  }

  public T setExternalTasklistHost(String externalTasklistHost) {
    this.externalTasklistHost = externalTasklistHost;
    return (T) this;
  }

  public Integer getExternalTasklistPort() {
    return externalTasklistPort;
  }

  public T setExternalTasklistPort(Integer externalTasklistPort) {
    this.externalTasklistPort = externalTasklistPort;
    return (T) this;
  }

  public String getExternalTasklistContextPath() {
    return externalTasklistContextPath;
  }

  public T setExternalTasklistContextPath(String externalTasklistContextPath) {
    this.externalTasklistContextPath = externalTasklistContextPath;
    return (T) this;
  }

  public List<String> getProcessesToAssert() {
    return processesToAssert;
  }

  public void setProcessesToAssert(List<String> processesToAssert) {
    this.processesToAssert = processesToAssert;
  }

  public void addProcess(String bpmnProcessId) {
    if (processesToAssert.contains(bpmnProcessId)) {
      throw new AssertionFailedError("Process was already created earlier: " + bpmnProcessId);
    }
    processesToAssert.add(bpmnProcessId);
  }
}
