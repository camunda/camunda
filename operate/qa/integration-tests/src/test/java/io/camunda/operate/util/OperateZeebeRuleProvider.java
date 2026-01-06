/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.client.CamundaClient;
import io.zeebe.containers.ZeebeContainer;
import java.time.Instant;
import org.junit.runner.Description;

public interface OperateZeebeRuleProvider {
  void starting(Description description);

  void updateRefreshInterval(String value);

  void refreshIndices(Instant instant);

  void finished(Description description);

  void failed(Throwable e, Description description);

  void startZeebe();

  void stopZeebe();

  String getPrefix();

  ZeebeContainer getZeebeContainer();

  CamundaClient getClient();

  boolean isMultitTenancyEnabled();
}
