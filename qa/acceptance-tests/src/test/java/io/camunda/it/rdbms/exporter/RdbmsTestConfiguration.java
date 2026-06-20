/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static io.camunda.configuration.api.physicaltenants.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.zeebe.scheduler.ActorScheduler;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Configuration
@EnableAutoConfiguration
@Import({RdbmsConfiguration.class, UnifiedConfigurationModule.class})
@PropertySource("classpath:rdbms-test-defaults.properties")
public class RdbmsTestConfiguration {

  @MockitoBean private ActorScheduler actorScheduler;

  @Bean(destroyMethod = "") // DataSource will be closed when closing RdbmsDataSources
  public DataSource dataSource(final RdbmsDataSources dataSources) {
    return dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
  }
}
