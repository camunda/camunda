/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  OperatePropertiesOverride.class,
  UnifiedConfigurationHelper.class,
  UnifiedConfiguration.class,
  SearchEngineConnectProperties.class,
  SearchEngineConnectPropertiesOverride.class
})
@TestPropertySource(
    properties = {
      "camunda.data.secondary-storage.type=opensearch",
      "camunda.database.awsEnabled=true"
    })
public class OperatePropertiesTest {

  @Test
  void shouldSetAwsEnabledTrue(@Autowired final OperateProperties operateProperties) {
    assertThat(operateProperties.getOpensearch().isAwsEnabled()).isTrue();
  }
}
