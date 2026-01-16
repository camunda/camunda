/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Instant;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.beans.factory.annotation.Autowired;

public class OperateZeebeRule extends TestWatcher {
  @Autowired public OperateZeebeRuleProvider operateZeebeRuleProvider;

  public void updateRefreshInterval(final String value) {
    operateZeebeRuleProvider.updateRefreshInterval(value);
  }

  public void refreshIndices(final Instant instant) {
    operateZeebeRuleProvider.refreshIndices(instant);
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    operateZeebeRuleProvider.failed(e, description);
  }

  @Override
  public void starting(final Description description) {
    operateZeebeRuleProvider.starting(description);
  }

  @Override
  public void finished(final Description description) {
    operateZeebeRuleProvider.finished(description);
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {
    operateZeebeRuleProvider.startZeebe();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    operateZeebeRuleProvider.stopZeebe();
  }

  public String getPrefix() {
    return operateZeebeRuleProvider.getPrefix();
  }

  //  public void setPrefix(String prefix) {
  //    this.prefix = prefix;
  //  }
  //
  public TestStandaloneBroker getZeebeBroker() {
    return operateZeebeRuleProvider.getZeebeBroker();
  }

  //  public void setOperateProperties(final OperateProperties operateProperties) {
  //    this.operateProperties = operateProperties;
  //  }
  //
  //  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
  //    this.zeebeEsClient = zeebeEsClient;
  //  }
  //
  public CamundaClient getClient() {
    return operateZeebeRuleProvider.getClient();
  }
}
