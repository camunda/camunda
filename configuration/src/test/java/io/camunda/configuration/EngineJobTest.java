/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.engine.JobsCfg;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
@TestPropertySource(
    properties = {"camunda.processing.engine.job.include-variables-in-job-completed-event=true"})
public class EngineJobTest {
  final BrokerBasedProperties brokerCfg;

  EngineJobTest(@Autowired final BrokerBasedProperties brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Test
  void shouldSetJobs() {
    assertThat(brokerCfg.getExperimental().getEngine().getJobs())
        .returns(true, JobsCfg::isIncludeVariablesInJobCompletedEvent);
  }
}
