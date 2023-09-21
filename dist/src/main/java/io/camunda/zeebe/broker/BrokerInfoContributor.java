/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.shared.AbstractConfigInfoContributor;
import io.camunda.zeebe.shared.ConfigSanitizingFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public final class BrokerInfoContributor extends AbstractConfigInfoContributor {
  @Autowired
  public BrokerInfoContributor(
      final @Qualifier("initializedBrokerCfg") BrokerCfg config,
      final ConfigSanitizingFunction sanitizingFunction) {
    super(config, sanitizingFunction);
  }
}
