/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.MappingRule;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MappingRuleMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final MappingRuleServices mappingRuleServices;
  private final RoleServices roleServices;
  private final TenantServices tenantServices;
  private final MappingRuleMigrationHandler mappingRuleMigrationHandler;

  public MappingRuleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final MappingRuleServices mappingRuleServices,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.mappingRuleServices = mappingRuleServices;
    this.roleServices = roleServices;
    this.tenantServices = tenantServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    mappingRuleMigrationHandler =
        new MappingRuleMigrationHandler(
            managementIdentityClient,
            mappingRuleServices,
            roleServices,
            tenantServices,
            CamundaAuthentication.none(),
            migrationProperties);
  }

  @Test
  public void shouldMigrateMappingRules() {
    // given
    when(managementIdentityClient.fetchMappingRules())
        .thenReturn(
            List.of(
                new MappingRule(
                    "rule1",
                    "claimName",
                    "claimValue",
                    Set.of(new Role("role1", "description1"), new Role("role2", "description2")),
                    Set.of(
                        new Tenant("tenant1", "tenantDescription1"),
                        new Tenant("tenant2", "tenantDescription2")))));
    when(mappingRuleServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(roleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    mappingRuleMigrationHandler.migrate();

    // then
    final var mappingRuleCapture = ArgumentCaptor.forClass(MappingRuleDTO.class);
    verify(mappingRuleServices, times(1)).createMappingRule(mappingRuleCapture.capture());
    final var mappingRuleDTO = mappingRuleCapture.getValue();
    assertThat(mappingRuleDTO.mappingRuleId()).isEqualTo("rule1");
    assertThat(mappingRuleDTO.name()).isEqualTo("rule1");
    assertThat(mappingRuleDTO.claimName()).isEqualTo("claimName");
    assertThat(mappingRuleDTO.claimValue()).isEqualTo("claimValue");

    final var roleCapture = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(roleServices, times(2)).addMember(roleCapture.capture());
    final var roleRequests = roleCapture.getAllValues();
    assertThat(roleRequests).hasSize(2);
    assertThat(roleRequests)
        .extracting(
            RoleMemberRequest::roleId, RoleMemberRequest::entityType, RoleMemberRequest::entityId)
        .contains(
            tuple("role1", EntityType.MAPPING_RULE, "rule1"),
            tuple("role2", EntityType.MAPPING_RULE, "rule1"));

    final var tenantCapture = ArgumentCaptor.forClass(TenantMemberRequest.class);
    verify(tenantServices, times(2)).addMember(tenantCapture.capture());
    final var tenantRequests = tenantCapture.getAllValues();
    assertThat(tenantRequests).hasSize(2);
    assertThat(tenantRequests)
        .extracting(
            TenantMemberRequest::tenantId,
            TenantMemberRequest::entityType,
            TenantMemberRequest::entityId)
        .contains(
            tuple("tenant1", EntityType.MAPPING_RULE, "rule1"),
            tuple("tenant2", EntityType.MAPPING_RULE, "rule1"));
  }

  @Test
  public void shouldContinueMigrationIfConflicts() {
    // given
    when(managementIdentityClient.fetchMappingRules())
        .thenReturn(
            List.of(
                new MappingRule(
                    "rule1",
                    "claimName",
                    "claimValue",
                    Set.of(new Role("role1", "description1"), new Role("role2", "description2")),
                    Set.of(
                        new Tenant("tenant1", "tenantDescription1"),
                        new Tenant("tenant2", "tenantDescription2")))));
    when(mappingRuleServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "mapping rule already exists")))));
    when(roleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            RoleIntent.ADD_ENTITY,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "member already exists")))));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            TenantIntent.ADD_ENTITY,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "member already exists")))));

    // when
    mappingRuleMigrationHandler.migrate();

    // then
    verify(mappingRuleServices, times(1)).createMappingRule(any(MappingRuleDTO.class));
    verify(roleServices, times(2)).addMember(any(RoleMemberRequest.class));
    verify(tenantServices, times(2)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnMappingRuleCreation() {
    // given
    when(managementIdentityClient.fetchMappingRules())
        .thenReturn(
            List.of(
                new MappingRule(
                    "rule1",
                    "claimName",
                    "claimValue",
                    Set.of(new Role("role1", "description1"), new Role("role2", "description2")),
                    Set.of(
                        new Tenant("tenant1", "tenantDescription1"),
                        new Tenant("tenant2", "tenantDescription2")))));
    when(mappingRuleServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(roleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    mappingRuleMigrationHandler.migrate();

    // then
    verify(mappingRuleServices, times(2)).createMappingRule(any(MappingRuleDTO.class));
    verify(roleServices, times(2)).addMember(any(RoleMemberRequest.class));
    verify(tenantServices, times(2)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnMappingRuleRoleAssignation() {
    // given
    when(managementIdentityClient.fetchMappingRules())
        .thenReturn(
            List.of(
                new MappingRule(
                    "rule1",
                    "claimName",
                    "claimValue",
                    Set.of(new Role("role1", "description1"), new Role("role2", "description2")),
                    Set.of(
                        new Tenant("tenant1", "tenantDescription1"),
                        new Tenant("tenant2", "tenantDescription2")))));
    when(mappingRuleServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(roleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    mappingRuleMigrationHandler.migrate();

    // then
    verify(mappingRuleServices, times(1)).createMappingRule(any(MappingRuleDTO.class));
    verify(roleServices, times(3)).addMember(any(RoleMemberRequest.class));
    verify(tenantServices, times(2)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnMappingRuleTenantAssignation() {
    // given
    when(managementIdentityClient.fetchMappingRules())
        .thenReturn(
            List.of(
                new MappingRule(
                    "rule1",
                    "claimName",
                    "claimValue",
                    Set.of(new Role("role1", "description1"), new Role("role2", "description2")),
                    Set.of(
                        new Tenant("tenant1", "tenantDescription1"),
                        new Tenant("tenant2", "tenantDescription2")))));
    when(mappingRuleServices.createMappingRule(any(MappingRuleDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(roleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    mappingRuleMigrationHandler.migrate();

    // then
    verify(mappingRuleServices, times(1)).createMappingRule(any(MappingRuleDTO.class));
    verify(roleServices, times(2)).addMember(any(RoleMemberRequest.class));
    verify(tenantServices, times(3)).addMember(any(TenantMemberRequest.class));
  }
}
