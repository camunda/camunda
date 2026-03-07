/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.auth.domain.port.outbound.AuthorizationReadPort;
import io.camunda.auth.domain.port.outbound.AuthorizationWritePort;
import io.camunda.auth.domain.port.outbound.GroupReadPort;
import io.camunda.auth.domain.port.outbound.GroupWritePort;
import io.camunda.auth.domain.port.outbound.MappingRuleReadPort;
import io.camunda.auth.domain.port.outbound.MappingRuleWritePort;
import io.camunda.auth.domain.port.outbound.RoleReadPort;
import io.camunda.auth.domain.port.outbound.RoleWritePort;
import io.camunda.auth.domain.port.outbound.TenantReadPort;
import io.camunda.auth.domain.port.outbound.TenantWritePort;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.domain.port.outbound.UserReadPort;
import io.camunda.auth.domain.port.outbound.UserWritePort;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch persistence auto-configuration for standalone deployments where the auth library
 * owns its data and indices. In external mode (e.g., Camunda monorepo), the consumer provides all
 * persistence beans — the auth library does not manage Elasticsearch indices.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "camunda.auth.persistence.type", havingValue = "elasticsearch")
@ConditionalOnClass(ElasticsearchClient.class)
public class ElasticsearchPersistenceAutoConfiguration {

  /**
   * All persistence ports for standalone mode. Uses auth-specific Elasticsearch indices (e.g.,
   * {@code camunda-auth-user}, {@code camunda-auth-role}). In external mode (e.g., Camunda
   * monorepo), the consumer provides all persistence beans — the auth library does not manage
   * Elasticsearch indices directly.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "camunda.auth.persistence.mode", havingValue = "standalone")
  @ConditionalOnBean(ElasticsearchClient.class)
  static class StandalonePersistencePorts {

    @Bean
    @ConditionalOnMissingBean(SessionPersistencePort.class)
    SessionPersistencePort elasticsearchSessionPersistencePort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchSessionPersistenceAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(TokenStorePort.class)
    TokenStorePort elasticsearchTokenStorePort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchTokenStoreAdapter(elasticsearchClient);
    }

    // --- Read ports ---

    @Bean
    @ConditionalOnMissingBean(UserReadPort.class)
    UserReadPort elasticsearchUserReadPort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchUserReadAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(RoleReadPort.class)
    RoleReadPort elasticsearchRoleReadPort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchRoleReadAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(TenantReadPort.class)
    TenantReadPort elasticsearchTenantReadPort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchTenantReadAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(GroupReadPort.class)
    GroupReadPort elasticsearchGroupReadPort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchGroupReadAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(MappingRuleReadPort.class)
    MappingRuleReadPort elasticsearchMappingRuleReadPort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchMappingRuleReadAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationReadPort.class)
    AuthorizationReadPort elasticsearchAuthorizationReadPort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchAuthorizationReadAdapter(elasticsearchClient);
    }

    // --- Write ports ---

    @Bean
    @ConditionalOnMissingBean(UserWritePort.class)
    UserWritePort elasticsearchUserWritePort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchUserWriteAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(RoleWritePort.class)
    RoleWritePort elasticsearchRoleWritePort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchRoleWriteAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(TenantWritePort.class)
    TenantWritePort elasticsearchTenantWritePort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchTenantWriteAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(GroupWritePort.class)
    GroupWritePort elasticsearchGroupWritePort(final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchGroupWriteAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(MappingRuleWritePort.class)
    MappingRuleWritePort elasticsearchMappingRuleWritePort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchMappingRuleWriteAdapter(elasticsearchClient);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationWritePort.class)
    AuthorizationWritePort elasticsearchAuthorizationWritePort(
        final ElasticsearchClient elasticsearchClient) {
      return new ElasticsearchAuthorizationWriteAdapter(elasticsearchClient);
    }
  }
}
