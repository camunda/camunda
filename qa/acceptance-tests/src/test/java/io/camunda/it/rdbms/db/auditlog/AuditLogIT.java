/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.auditlog;

import static io.camunda.it.rdbms.db.fixtures.AuditLogFixtures.createAndSaveAuditLog;
import static io.camunda.it.rdbms.db.fixtures.AuditLogFixtures.createAndSaveRandomAuditLogs;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.resourceAccessChecksFromTenantIds;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUDIT_LOG;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.sort.AuditLogSort;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuditLogIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldGetAuditLogById(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var original = AuditLogFixtures.createRandomized(b -> b);
    createAndSaveAuditLog(rdbmsWriters, original);

    final var instance =
        auditLogReader.getById(
            String.valueOf(original.auditLogKey()), resourceAccessChecksFromTenantIds());

    compareAuditLog(instance, original);
  }

  @TestTemplate
  public void shouldFindAuditLogByEntityType(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var original = AuditLogFixtures.createRandomized(b -> b);
    createAndSaveAuditLog(rdbmsWriters, original);

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.entityTypes(original.entityType().name()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(1000))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::auditLogKey)
        .contains(original.auditLogKey());
  }

  @TestTemplate
  public void shouldFindAuditLogByProcessInstanceKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var original = AuditLogFixtures.createRandomized(b -> b);
    createAndSaveAuditLog(rdbmsWriters, original);

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(original.processInstanceKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();

    compareAuditLog(instance, original);
  }

  @TestTemplate
  public void shouldFindAllAuditLogsPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final Long processInstanceKey = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(processInstanceKey))
                        .sort(s -> s.timestamp().asc().entityType().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllAuditLogsPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final Long processInstanceKey = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, 120, b -> b.processInstanceKey(processInstanceKey));

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(processInstanceKey))
                        .sort(s -> s.timestamp().asc().entityType().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAuditLogWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var original = AuditLogFixtures.createRandomized(b -> b);
    createAndSaveAuditLog(rdbmsWriters, original);
    createAndSaveRandomAuditLogs(rdbmsWriters);

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.entityKeys(original.entityKey())
                                    .entityTypes(original.entityType().name())
                                    .operationTypes(original.operationType().name())
                                    .actorTypes(original.actorType().name())
                                    .actorIds(original.actorId())
                                    .results(original.result().name())
                                    .categories(original.category().name())
                                    .processInstanceKeys(original.processInstanceKey())
                                    .processDefinitionKeys(original.processDefinitionKey())
                                    .processDefinitionIds(original.processDefinitionId())
                                    .tenantIds(original.tenantId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().entityKey()).isEqualTo(original.entityKey());
  }

  @TestTemplate
  public void shouldFindAuditLogWithSearchAfter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processInstanceKey = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));
    final var sort = AuditLogSort.of(s -> s.timestamp().asc().entityType().asc().entityKey().asc());
    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(processInstanceKey))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(processInstanceKey))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(f -> f.processInstanceKeys(processInstanceKey))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldFilterByAuthorizedCategories(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    // Create audit logs with different categories
    final var adminLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters, b -> b.category(AuditLogOperationCategory.ADMIN));
    final var userTaskLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters, b -> b.category(AuditLogOperationCategory.USER_TASKS));
    final var deployedResourcesLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters, b -> b.category(AuditLogOperationCategory.DEPLOYED_RESOURCES));

    // Create ResourceAccessChecks with AUDIT_LOG resource type
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(AUDIT_LOG)
                            .resourceIds(
                                List.of(
                                    AuditLogOperationCategory.ADMIN.name(),
                                    AuditLogOperationCategory.USER_TASKS.name())))),
            TenantCheck.disabled());

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(b -> b.page(p -> p.size(1000))), resourceAccessChecks);

    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(adminLog.auditLogKey(), userTaskLog.auditLogKey())
        .doesNotContain(deployedResourcesLog.auditLogKey());
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionWithReadProcessInstance(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";
    final var processDefId3 = "process-def-3";

    // Create audit logs with different process definitions
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId1).category(AuditLogOperationCategory.ADMIN));
    final var log2 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId2).category(AuditLogOperationCategory.ADMIN));
    final var log3 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId3).category(AuditLogOperationCategory.ADMIN));

    // Create ResourceAccessChecks with PROCESS_DEFINITION + READ_PROCESS_INSTANCE
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_PROCESS_INSTANCE)
                            .resourceIds(List.of(processDefId1, processDefId2)))),
            TenantCheck.disabled());

    final var searchResult = auditLogReader.search(AuditLogQuery.of(b -> b), resourceAccessChecks);

    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(log1.auditLogKey(), log2.auditLogKey())
        .doesNotContain(log3.auditLogKey());
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionWithReadProcessInstanceWildcard(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";

    // Create audit logs with different process definitions and categories
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId1).category(AuditLogOperationCategory.ADMIN));
    final var log2 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId2).category(AuditLogOperationCategory.ADMIN));
    final var userTaskLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId1)
                    .category(AuditLogOperationCategory.USER_TASKS));
    // Create a log without process definition ID - should NOT be returned
    final var logWithoutProcessDefId =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(null).category(AuditLogOperationCategory.ADMIN));

    // Create ResourceAccessChecks with wildcard access
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_PROCESS_INSTANCE)
                            .resourceIds(List.of("*")))),
            TenantCheck.disabled());

    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(b -> b.page(p -> p.size(1000))), resourceAccessChecks);

    // Should return all logs that have a process definition ID (including USER_TASKS)
    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(log1.auditLogKey(), log2.auditLogKey(), userTaskLog.auditLogKey())
        .doesNotContain(logWithoutProcessDefId.auditLogKey());
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionWithReadUserTask(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";
    final var processDefId3 = "process-def-3";

    // Create audit logs with different process definitions
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId1)
                    .category(AuditLogOperationCategory.USER_TASKS));
    final var log2 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId2)
                    .category(AuditLogOperationCategory.USER_TASKS));
    final var log3 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId3)
                    .category(AuditLogOperationCategory.USER_TASKS));

    // Create ResourceAccessChecks with PROCESS_DEFINITION + READ_USER_TASK
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_USER_TASK)
                            .resourceIds(List.of(processDefId1, processDefId2)))),
            TenantCheck.disabled());

    final var searchResult = auditLogReader.search(AuditLogQuery.of(b -> b), resourceAccessChecks);

    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(log1.auditLogKey(), log2.auditLogKey())
        .doesNotContain(log3.auditLogKey());
  }

  @TestTemplate
  public void shouldFilterByProcessDefinitionWithReadUserTaskWildcard(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";

    // Create audit logs with different process definitions and categories
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId1)
                    .category(AuditLogOperationCategory.USER_TASKS));
    final var log2 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId2)
                    .category(AuditLogOperationCategory.USER_TASKS));
    final var adminLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId1).category(AuditLogOperationCategory.ADMIN));

    // Create ResourceAccessChecks with wildcard access
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_USER_TASK)
                            .resourceIds(List.of("*")))),
            TenantCheck.disabled());

    final var searchResult = auditLogReader.search(AuditLogQuery.of(b -> b), resourceAccessChecks);

    // Should return all USER_TASKS category logs
    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(log1.auditLogKey(), log2.auditLogKey())
        .doesNotContain(adminLog.auditLogKey());
  }

  @TestTemplate
  public void shouldFilterByCompositeAuthorization(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";

    // Create audit logs with different categories and process definitions
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId(processDefId1).category(AuditLogOperationCategory.ADMIN));
    final var log2 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId2)
                    .category(AuditLogOperationCategory.USER_TASKS));
    final var log3 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters, b -> b.category(AuditLogOperationCategory.DEPLOYED_RESOURCES));
    final var log4 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b -> b.processDefinitionId("process-def-3").category(AuditLogOperationCategory.ADMIN));

    // Create composite authorization with multiple resource types and permissions
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                AuthorizationConditions.anyOf(
                    Authorization.of(
                        a ->
                            a.resourceType(AUDIT_LOG)
                                .resourceIds(
                                    List.of(AuditLogOperationCategory.DEPLOYED_RESOURCES.name()))),
                    Authorization.of(
                        a ->
                            a.resourceType(PROCESS_DEFINITION)
                                .permissionType(READ_PROCESS_INSTANCE)
                                .resourceIds(List.of(processDefId1))),
                    Authorization.of(
                        a ->
                            a.resourceType(PROCESS_DEFINITION)
                                .permissionType(READ_USER_TASK)
                                .resourceIds(List.of(processDefId2))))),
            TenantCheck.disabled());

    final var searchResult = auditLogReader.search(AuditLogQuery.of(b -> b), resourceAccessChecks);

    assertThat(searchResult.items())
        .extracting(AuditLogEntity::auditLogKey)
        .contains(log1.auditLogKey(), log2.auditLogKey(), log3.auditLogKey())
        .doesNotContain(log4.auditLogKey());
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoAuthorizationMatch(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    // Create audit logs
    AuditLogFixtures.createAndSaveAuditLog(
        rdbmsWriters,
        b -> b.processDefinitionId("process-def-1").category(AuditLogOperationCategory.ADMIN));

    // Create ResourceAccessChecks with non-matching authorization
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_PROCESS_INSTANCE)
                            .resourceIds(List.of("non-existent-process")))),
            TenantCheck.disabled());

    final var searchResult = auditLogReader.search(AuditLogQuery.of(b -> b), resourceAccessChecks);

    assertThat(searchResult.total()).isZero();
    assertThat(searchResult.items()).isEmpty();
  }

  @TestTemplate
  public void shouldReturnAllWhenAuthorizationDisabled(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final Long processInstanceKey = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processInstanceKey(processInstanceKey));

    // Search with disabled authorization
    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))),
            ResourceAccessChecks.disabled());

    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(20);
  }

  @TestTemplate
  public void shouldCombineAuthorizationWithOtherFilters(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var processDefId1 = "process-def-1";
    final var processDefId2 = "process-def-2";
    final var processInstanceKey1 = nextKey();
    final var processInstanceKey2 = nextKey();

    // Create audit logs with different process definitions and process instances
    final var log1 =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriters,
            b ->
                b.processDefinitionId(processDefId1)
                    .processInstanceKey(processInstanceKey1)
                    .category(AuditLogOperationCategory.ADMIN));
    AuditLogFixtures.createAndSaveAuditLog(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefId1)
                .processInstanceKey(processInstanceKey2)
                .category(AuditLogOperationCategory.ADMIN));
    AuditLogFixtures.createAndSaveAuditLog(
        rdbmsWriters,
        b ->
            b.processDefinitionId(processDefId2)
                .processInstanceKey(processInstanceKey1)
                .category(AuditLogOperationCategory.ADMIN));

    // Create ResourceAccessChecks with authorization
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.resourceType(PROCESS_DEFINITION)
                            .permissionType(READ_PROCESS_INSTANCE)
                            .resourceIds(List.of(processDefId1)))),
            TenantCheck.disabled());

    // Search with both authorization and filter
    final var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey1))),
            resourceAccessChecks);

    // Should only return log1 (matches both authorization and filter)
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().auditLogKey()).isEqualTo(log1.auditLogKey());
  }

  private void compareAuditLog(final AuditLogEntity instance, final AuditLogDbModel original) {
    assertThat(instance).isNotNull();
    assertThat(instance)
        .usingRecursiveComparison()
        // timestamp field is ignored because different engines produce different precisions
        .ignoringFields("timestamp")
        .isEqualTo(original);
    assertThat(instance.entityKey()).isEqualTo(original.entityKey());
    assertThat(instance.entityType()).isEqualTo(original.entityType());
  }

  @TestTemplate
  public void shouldDeleteProcessDefinitionRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();
    final AuditLogWriter auditLogWriter = rdbmsWriters.getAuditLogWriter();

    final var processDefinitionKey1 = nextKey();
    final var processDefinitionKey2 = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey1));
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey2));

    var searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processDefinitionKeys(
                                    processDefinitionKey1, processDefinitionKey2))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(1000))));
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::processDefinitionKey)
        .containsOnly(processDefinitionKey1, processDefinitionKey2);

    // when
    auditLogWriter.deleteProcessDefinitionRelatedData(
        List.of(processDefinitionKey1, processDefinitionKey2), 1000);

    // then
    searchResult =
        auditLogReader.search(
            AuditLogQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.processDefinitionKeys(
                                    processDefinitionKey1, processDefinitionKey2))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(1000))));
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items()).isEmpty();
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();
    final AuditLogWriter auditLogWriter = rdbmsWriters.getAuditLogWriter();

    final var processInstanceKey1 = nextKey();
    final var processInstanceKey2 = nextKey();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processInstanceKey(processInstanceKey1));
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.processInstanceKey(processInstanceKey2));

    final AuditLogQuery auditLogQuery =
        AuditLogQuery.of(
            b ->
                b.filter(f -> f.processInstanceKeys(processInstanceKey1, processInstanceKey2))
                    .page(p -> p.from(0).size(1000)));
    var searchResult = auditLogReader.search(auditLogQuery);
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::processInstanceKey)
        .containsOnly(processInstanceKey1, processInstanceKey2);

    // when
    final int deleted =
        auditLogWriter.deleteProcessInstanceRelatedData(List.of(processInstanceKey1), 1000);

    // then
    assertThat(deleted).isEqualTo(20);
    searchResult = auditLogReader.search(auditLogQuery);
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::processInstanceKey)
        .containsOnly(processInstanceKey2);
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();
    final AuditLogWriter auditLogWriter = rdbmsWriters.getAuditLogWriter();

    final var processInstanceKey1 = nextKey();
    final var processInstanceKey2 = nextKey();
    final var processInstanceKey3 = nextKey();
    final var rootProcessInstanceKey1 = nextKey();
    final var rootProcessInstanceKey2 = nextKey();
    createAndSaveRandomAuditLogs(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey1)
                .rootProcessInstanceKey(rootProcessInstanceKey1));
    createAndSaveRandomAuditLogs(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey2)
                .rootProcessInstanceKey(rootProcessInstanceKey2));
    createAndSaveRandomAuditLogs(
        rdbmsWriters,
        b ->
            b.processInstanceKey(processInstanceKey3)
                .rootProcessInstanceKey(rootProcessInstanceKey2));

    final AuditLogQuery auditLogQuery =
        AuditLogQuery.of(
            b ->
                b.filter(
                        f ->
                            f.processInstanceKeys(
                                processInstanceKey1, processInstanceKey2, processInstanceKey3))
                    .page(p -> p.from(0).size(1000)));
    var searchResult = auditLogReader.search(auditLogQuery);
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::rootProcessInstanceKey)
        .containsOnly(rootProcessInstanceKey1, rootProcessInstanceKey2);
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::processInstanceKey)
        .containsOnly(processInstanceKey1, processInstanceKey2, processInstanceKey3);

    // when
    final int deleted =
        auditLogWriter.deleteRootProcessInstanceRelatedData(List.of(rootProcessInstanceKey2), 1000);

    // then
    assertThat(deleted).isEqualTo(40);
    searchResult = auditLogReader.search(auditLogQuery);
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::rootProcessInstanceKey)
        .containsOnly(rootProcessInstanceKey1);
    assertThat(searchResult.items())
        .isNotEmpty()
        .extracting(AuditLogEntity::processInstanceKey)
        .containsOnly(processInstanceKey1);
  }
}
