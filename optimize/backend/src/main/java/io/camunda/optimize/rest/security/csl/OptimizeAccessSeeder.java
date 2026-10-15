/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.model.config.initialization.ConfiguredRole;
import io.camunda.security.api.model.config.initialization.ConfiguredTenant;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * POC: seeds mapping rules, roles and tenant assignments from CSL's own {@code
 * camunda.security.initialization.*} keys, the same keys the Hub POC seeds from. Inserts what is
 * missing and never changes or removes existing rows. Fails startup when no {@code admin} role
 * exists, so nobody is locked out of administering access.
 */
@Component
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = OptimizeAccessRoles.ACCESS_ROLES_ENABLED_PROPERTY,
    havingValue = "true")
public class OptimizeAccessSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeAccessSeeder.class);

  private final CamundaSecurityLibraryProperties securityProperties;
  private final AccessRepository accessRepository;
  private final TenantMembershipRepository tenantMembershipRepository;

  public OptimizeAccessSeeder(
      final CamundaSecurityLibraryProperties securityProperties,
      final AccessRepository accessRepository,
      final TenantMembershipRepository tenantMembershipRepository) {
    this.securityProperties = securityProperties;
    this.accessRepository = accessRepository;
    this.tenantMembershipRepository = tenantMembershipRepository;
  }

  @Override
  public void run(final ApplicationArguments args) {
    final var initialization = securityProperties.getInitialization();
    int rules = 0;
    for (final var configured : initialization.getMappingRules()) {
      if (accessRepository.createMappingRuleIfMissing(
          new AccessMappingRuleDto(
              configured.mappingRuleId(), configured.claimName(), configured.claimValue()))) {
        rules++;
      }
    }
    int roles = 0;
    for (final ConfiguredRole configured : initialization.getRoles()) {
      final var role = new AccessRoleDto(configured.roleId(), configured.name());
      role.setDescription(configured.description());
      role.setMappingRuleIds(orEmpty(configured.mappingRules()));
      role.setUserIds(orEmpty(configured.users()));
      role.setGroupIds(orEmpty(configured.groups()));
      role.setClientIds(orEmpty(configured.clients()));
      if (accessRepository.createRoleIfMissing(role)) {
        roles++;
      }
    }
    for (final ConfiguredTenant tenant : initialization.getTenants()) {
      final String id = tenant.tenantId();
      assign(id, MemberType.USER, tenant.users());
      assign(id, MemberType.CLIENT, tenant.clients());
      assign(id, MemberType.GROUP, tenant.groups());
      assign(id, MemberType.ROLE, tenant.roles());
      assign(id, MemberType.MAPPING_RULE, tenant.mappingRules());
    }
    LOG.info(
        "Optimize access data: {} mapping rules and {} roles inserted ({} rules, {} roles in total)."
            + " Config only adds missing rows, it never updates or removes existing ones.",
        rules,
        roles,
        accessRepository.findAllMappingRules().size(),
        accessRepository.findAllRoles().size());
    if (accessRepository.findAllRoles().stream()
        .noneMatch(role -> OptimizeAccessRoles.ADMIN_ROLE_ID.equals(role.getId()))) {
      throw new IllegalStateException(
          "No '"
              + OptimizeAccessRoles.ADMIN_ROLE_ID
              + "' role exists. Define one under camunda.security.initialization.roles so that"
              + " someone can administer Optimize access.");
    }
  }

  private void assign(final String tenantId, final MemberType type, final List<String> memberIds) {
    for (final String memberId : orEmpty(memberIds)) {
      tenantMembershipRepository.assign(new TenantMembershipDto(tenantId, type, memberId));
    }
  }

  private static List<String> orEmpty(final List<String> values) {
    return values == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(values);
  }
}
