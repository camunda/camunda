/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeContainer;
import org.junit.runner.Description;

import java.time.Instant;

public interface OperateZeebeRuleProvider {
  void starting(Description description);

  void updateRefreshInterval(String value);

  void refreshIndices(Instant instant);

  void finished(Description description);

  void failed(Throwable e, Description description);

  void startZeebe();

  void stop();

  String getPrefix();

  ZeebeContainer getZeebeContainer();

  ZeebeClient getClient();

  boolean isMultitTenancyEnabled();
}
