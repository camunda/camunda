/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableAutoConfiguration
@Import({RdbmsConfiguration.class, UnifiedConfigurationModule.class})
@PropertySource("classpath:rdbms-test-defaults.properties")
public class RdbmsTestConfiguration {

  @Bean(destroyMethod = "") // DataSource will be closed when closing RdbmsDataSources
  public DataSource dataSource(final RdbmsDataSources dataSources) {
    return dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
  }

  /** Support for transactional tests with @RdbmsDataJdbcTest or @DataJdbcTest */
  @Bean
  public PlatformTransactionManager platformTransactionManager(final DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  public BrokerTopologyManager brokerTopologyManager() {
    final var member = mock(BrokerPartitionState.class);
    when(member.mode()).thenReturn(Mode.PROCESSING);
    final SortedMap<MemberId, BrokerPartitionState> members = new TreeMap<>();
    members.put(MemberId.from("0"), member);
    final var partitionGroup = mock(PartitionGroupConfiguration.class);
    when(partitionGroup.members()).thenReturn(members);
    final var clusterConfiguration = mock(CurrentClusterConfiguration.class);
    when(clusterConfiguration.partitionGroup(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(partitionGroup);
    final var topologyManager = mock(BrokerTopologyManager.class);
    when(topologyManager.getClusterConfiguration()).thenReturn(clusterConfiguration);
    return topologyManager;
  }
}
