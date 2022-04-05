/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util.migration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.testcontainers.containers.Network;

public class TestContext {

  private File zeebeDataFolder;

  private Network network;
  private String externalElsHost;
  private Integer externalElsPort;
  private String internalElsHost;
  private Integer internalElsPort;

  private String externalZeebeContactPoint;
  private String internalZeebeContactPoint;

  private String externalTasklistHost;
  private Integer externalTasklistPort;
  private String externalTasklistContextPath = "/";

  private List<String> processesToAssert = new ArrayList<>();

  public File getZeebeDataFolder() {
    return zeebeDataFolder;
  }

  public void setZeebeDataFolder(File zeebeDataFolder) {
    this.zeebeDataFolder = zeebeDataFolder;
  }

  public Network getNetwork() {
    return network;
  }

  public void setNetwork(Network network) {
    this.network = network;
  }

  public String getExternalElsHost() {
    return externalElsHost;
  }

  public void setExternalElsHost(String externalElsHost) {
    this.externalElsHost = externalElsHost;
  }

  public Integer getExternalElsPort() {
    return externalElsPort;
  }

  public void setExternalElsPort(Integer externalElsPort) {
    this.externalElsPort = externalElsPort;
  }

  public String getInternalElsHost() {
    return internalElsHost;
  }

  public void setInternalElsHost(String internalElsHost) {
    this.internalElsHost = internalElsHost;
  }

  public Integer getInternalElsPort() {
    return internalElsPort;
  }

  public void setInternalElsPort(Integer internalElsPort) {
    this.internalElsPort = internalElsPort;
  }

  public String getExternalZeebeContactPoint() {
    return externalZeebeContactPoint;
  }

  public void setExternalZeebeContactPoint(String externalZeebeContactPoint) {
    this.externalZeebeContactPoint = externalZeebeContactPoint;
  }

  public String getInternalZeebeContactPoint() {
    return internalZeebeContactPoint;
  }

  public void setInternalZeebeContactPoint(String internalZeebeContactPoint) {
    this.internalZeebeContactPoint = internalZeebeContactPoint;
  }

  public String getExternalTasklistHost() {
    return externalTasklistHost;
  }

  public void setExternalTasklistHost(String externalTasklistHost) {
    this.externalTasklistHost = externalTasklistHost;
  }

  public Integer getExternalTasklistPort() {
    return externalTasklistPort;
  }

  public void setExternalTasklistPort(Integer externalTasklistPort) {
    this.externalTasklistPort = externalTasklistPort;
  }

  public List<String> getProcessesToAssert() {
    return processesToAssert;
  }

  public void setProcessesToAssert(List<String> processesToAssert) {
    this.processesToAssert = processesToAssert;
  }

  public String getExternalTasklistContextPath() {
    return externalTasklistContextPath;
  }

  public void setExternalTasklistContextPath(String externalTasklistContextPath) {
    this.externalTasklistContextPath = externalTasklistContextPath;
  }

  public void addProcess(String bpmnProcessId) {
    if (processesToAssert.contains(bpmnProcessId)) {
      throw new AssertionFailedError("Process was already created earlier: " + bpmnProcessId);
    }
    processesToAssert.add(bpmnProcessId);
  }
}
