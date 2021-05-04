/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util;

import io.camunda.zeebe.test.EmbeddedBrokerRule;

public interface EmbeddedZeebeConfigurer {

  void injectPrefixToZeebeConfig(EmbeddedBrokerRule brokerRule, String exporterId, String prefix);

}
