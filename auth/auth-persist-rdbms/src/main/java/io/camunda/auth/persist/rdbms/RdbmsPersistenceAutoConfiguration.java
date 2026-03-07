/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

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
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RDBMS persistence auto-configuration for standalone deployments where the auth library owns its
 * data and tables. In external mode (e.g., Camunda monorepo), the consumer provides all persistence
 * beans — the auth library does not manage RDBMS tables directly.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "camunda.auth.persistence.type", havingValue = "rdbms")
@ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
@MapperScan("io.camunda.auth.persist.rdbms")
public class RdbmsPersistenceAutoConfiguration {

  /**
   * All persistence ports for standalone mode. Uses auth-specific RDBMS tables. In external mode,
   * the consumer provides all persistence beans.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "camunda.auth.persistence.mode", havingValue = "standalone")
  static class StandalonePersistencePorts {

    @Bean
    @ConditionalOnMissingBean(SessionPersistencePort.class)
    SessionPersistencePort rdbmsSessionPersistencePort(final WebSessionMapper mapper) {
      return new RdbmsSessionPersistenceAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TokenStorePort.class)
    TokenStorePort rdbmsTokenStorePort(final TokenExchangeAuditMapper mapper) {
      return new RdbmsTokenStoreAdapter(mapper);
    }

    // --- Read ports ---

    @Bean
    @ConditionalOnMissingBean(UserReadPort.class)
    UserReadPort rdbmsUserReadPort(final UserMapper mapper) {
      return new RdbmsUserReadAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(RoleReadPort.class)
    RoleReadPort rdbmsRoleReadPort(final RoleMapper mapper) {
      return new RdbmsRoleReadAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TenantReadPort.class)
    TenantReadPort rdbmsTenantReadPort(final TenantMapper mapper) {
      return new RdbmsTenantReadAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(GroupReadPort.class)
    GroupReadPort rdbmsGroupReadPort(final GroupMapper mapper) {
      return new RdbmsGroupReadAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(MappingRuleReadPort.class)
    MappingRuleReadPort rdbmsMappingRuleReadPort(final MappingRuleMapper mapper) {
      return new RdbmsMappingRuleReadAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationReadPort.class)
    AuthorizationReadPort rdbmsAuthorizationReadPort(final AuthorizationMapper mapper) {
      return new RdbmsAuthorizationReadAdapter(mapper);
    }

    // --- Write ports ---

    @Bean
    @ConditionalOnMissingBean(UserWritePort.class)
    UserWritePort rdbmsUserWritePort(final UserMapper mapper) {
      return new RdbmsUserWriteAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(RoleWritePort.class)
    RoleWritePort rdbmsRoleWritePort(final RoleMapper mapper) {
      return new RdbmsRoleWriteAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TenantWritePort.class)
    TenantWritePort rdbmsTenantWritePort(final TenantMapper mapper) {
      return new RdbmsTenantWriteAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(GroupWritePort.class)
    GroupWritePort rdbmsGroupWritePort(final GroupMapper mapper) {
      return new RdbmsGroupWriteAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(MappingRuleWritePort.class)
    MappingRuleWritePort rdbmsMappingRuleWritePort(final MappingRuleMapper mapper) {
      return new RdbmsMappingRuleWriteAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationWritePort.class)
    AuthorizationWritePort rdbmsAuthorizationWritePort(final AuthorizationMapper mapper) {
      return new RdbmsAuthorizationWriteAdapter(mapper);
    }
  }
}
