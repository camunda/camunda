/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;

abstract class AbstractBatchOperationTest {

  protected static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  protected static final int DEFAULT_QUERY_PAGE_SIZE = 10000;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();
  protected final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))))
          .withEngineConfig(cfg -> cfg.setBatchOperationQueryPageSize(DEFAULT_QUERY_PAGE_SIZE))
          .withSearchClientsProxy(searchClientsProxy);

  @Before
  public void setUp() {
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);
  }

  protected long createNewCancelProcessInstanceBatchOperation(final Set<Long> itemKeys) {
    return createNewCancelProcessInstanceBatchOperation(itemKeys, null);
  }

  protected long createNewCancelProcessInstanceBatchOperation(
      final Set<Long> itemKeys, final Map<String, Object> claims) {
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                itemKeys.stream().map(this::fakeProcessInstanceEntity).collect(Collectors.toList()))
            .total(itemKeys.size())
            .build();

    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(result);

    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    DirectBuffer authenticationBuffer = null;
    if (claims != null) {
      authenticationBuffer = convertToBuffer(CamundaAuthentication.of(b -> b.claims(claims)));
    }

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .withFilter(filterBuffer)
        .withAuthentication(authenticationBuffer)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getBatchOperationKey();
  }

  protected long createNewModifyProcessInstanceBatchOperation(
      final Set<Long> itemKeys,
      final String sourceElementId,
      final String targetElementId,
      final Map<String, Object> claims) {
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                itemKeys.stream().map(this::fakeProcessInstanceEntity).collect(Collectors.toList()))
            .total(itemKeys.size())
            .build();

    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(result);

    final var filterBuffer = convertToBuffer(new ProcessInstanceFilter.Builder().build());

    final var modificationPlan = new BatchOperationProcessInstanceModificationPlan();

    final var mappingInstruction = new BatchOperationProcessInstanceModificationMoveInstruction();
    mappingInstruction.setSourceElementId(sourceElementId);
    mappingInstruction.setTargetElementId(targetElementId);
    modificationPlan.addMoveInstruction(mappingInstruction);

    DirectBuffer authenticationBuffer = null;
    if (claims != null) {
      authenticationBuffer = convertToBuffer(CamundaAuthentication.of(b -> b.claims(claims)));
    }

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.MODIFY_PROCESS_INSTANCE)
        .withFilter(filterBuffer)
        .withAuthentication(authenticationBuffer)
        .withModificationPlan(modificationPlan)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getBatchOperationKey();
  }

  protected long createNewFailedCancelProcessInstanceBatchOperation() {
    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenThrow(new RuntimeException("You are playing with fire!"));

    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .withFilter(filterBuffer)
        .waitForInitialized()
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getBatchOperationKey();
  }

  protected long createNewResolveIncidentsBatchOperation(
      final Map<Long, Set<Long>> piAndIncidentKeys, final Map<String, Object> claims) {
    final var processInstanceResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                piAndIncidentKeys.keySet().stream()
                    .map(this::fakeProcessInstanceEntity)
                    .collect(Collectors.toList()))
            .total(piAndIncidentKeys.size())
            .build();
    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(processInstanceResult);
    final var incidentKeys =
        piAndIncidentKeys.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    final var incidentResult =
        new SearchQueryResult.Builder<IncidentEntity>()
            .items(
                incidentKeys.stream()
                    .map(k -> fakeIncidentEntity(k, k))
                    .collect(Collectors.toList()))
            .total(incidentKeys.size())
            .build();
    when(searchClientsProxy.searchIncidents(Mockito.any(IncidentQuery.class)))
        .thenReturn(incidentResult);

    final var filterBuffer =
        convertToBuffer(
            new ProcessInstanceFilter.Builder().processInstanceKeys(1L, 3L, 8L).build());

    DirectBuffer authenticationBuffer = null;
    if (claims != null) {
      authenticationBuffer = convertToBuffer(CamundaAuthentication.of(b -> b.claims(claims)));
    }

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.RESOLVE_INCIDENT)
        .withFilter(filterBuffer)
        .withAuthentication(authenticationBuffer)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getBatchOperationKey();
  }

  protected long createNewMigrateProcessesBatchOperation(
      final List<Long> itemKeys,
      final long targetProcessDefinitionId,
      final Map<String, String> mappingInstructions,
      final Map<String, Object> claims) {
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(itemKeys.stream().map(this::fakeProcessInstanceEntity).toList())
            .total(itemKeys.size())
            .build();
    when(searchClientsProxy.searchProcessInstances(Mockito.any(ProcessInstanceQuery.class)))
        .thenReturn(result);

    final var filterBuffer = convertToBuffer(new ProcessInstanceFilter.Builder().build());

    final var migrationPlan = new BatchOperationProcessInstanceMigrationPlan();
    migrationPlan.setTargetProcessDefinitionKey(targetProcessDefinitionId);

    mappingInstructions.entrySet().stream()
        .map(
            entry -> {
              final var mappingInstruction = new ProcessInstanceMigrationMappingInstruction();
              mappingInstruction.setSourceElementId(entry.getKey());
              mappingInstruction.setTargetElementId(entry.getValue());
              return mappingInstruction;
            })
        .forEach(migrationPlan::addMappingInstruction);

    DirectBuffer authenticationBuffer = null;
    if (claims != null) {
      authenticationBuffer = convertToBuffer(CamundaAuthentication.of(b -> b.claims(claims)));
    }

    return engine
        .batchOperation()
        .newCreation(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
        .withFilter(filterBuffer)
        .withAuthentication(authenticationBuffer)
        .withMigrationPlan(migrationPlan)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getBatchOperationKey();
  }

  protected static UnsafeBuffer convertToBuffer(final Object object) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(object));
  }

  protected ProcessInstanceEntity fakeProcessInstanceEntity(final long processInstanceKey) {
    return new ProcessInstanceEntity(
        processInstanceKey,
        null,
        null,
        -1,
        null,
        -1L,
        -1L,
        -1L,
        null,
        null,
        null,
        false,
        null,
        null);
  }

  protected IncidentEntity fakeIncidentEntity(
      final long incidentKey, final long processInstanceKey) {
    return new IncidentEntity(
        incidentKey,
        -1L, // processDefinitionKey
        null, // processDefinitionId
        processInstanceKey, // processInstanceKey
        IncidentEntity.ErrorType.UNSPECIFIED,
        "Fake incident message",
        "flowNodeId",
        -1L, // flowNodeInstanceKey
        null, // creationTime
        IncidentEntity.IncidentState.ACTIVE,
        -1L, // jobKey
        null // tenantId
        );
  }

  protected UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  protected void addPermissionsToUser(
      final UserRecordValue user, final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.BATCH)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }

  protected void addProcessDefinitionPermissionsToUser(
      final UserRecordValue user, final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }
}
