/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(value = {"broker", "restore"})
public class BrokerBasedPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final BrokerBasedProperties legacyBrokerBasedProperties;

  public BrokerBasedPropertiesOverride(
      UnifiedConfiguration unifiedConfiguration, BrokerBasedProperties properties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyBrokerBasedProperties = properties;
  }

  @Bean
  @Primary
  public BrokerBasedProperties brokerBasedProperties() {
    final BrokerBasedProperties override = new BrokerBasedProperties();
    BeanUtils.copyProperties(legacyBrokerBasedProperties, override);

    // TODO: Populate the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }
}
