/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.domain.store.CompositeTokenStore;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for persistence adapters. Wires RDBMS and/or Elasticsearch stores into a
 * composite store when multiple are enabled.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.token-exchange.enabled", havingValue = "true")
public class CamundaAuthPersistenceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(TokenStorePort.class)
  public TokenStorePort compositeTokenStore(final List<TokenStorePort> stores) {
    if (stores.size() == 1) {
      return stores.get(0);
    }
    return new CompositeTokenStore(stores);
  }
}
