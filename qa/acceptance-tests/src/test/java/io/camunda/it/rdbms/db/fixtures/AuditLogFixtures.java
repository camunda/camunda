/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.AuditLogEntity.AuditLogTenantScope;
import io.camunda.search.entities.BatchOperationType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public final class AuditLogFixtures extends CommonFixtures {

  private AuditLogFixtures() {}

  public static AuditLogDbModel createRandomized(
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    final var builder =
        new AuditLogDbModel.Builder()
            .auditLogKey("audit-" + generateRandomString(15))
            .entityKey(String.valueOf(nextKey()))
            .entityType(randomEnum(AuditLogEntityType.class))
            .operationType(randomEnum(AuditLogOperationType.class))
            .entityVersion(RANDOM.nextInt(100))
            .entityValueType((short) RANDOM.nextInt(10))
            .entityOperationIntent((short) RANDOM.nextInt(10))
            .batchOperationKey(nextKey())
            .batchOperationType(randomEnum(BatchOperationType.class))
            .timestamp(OffsetDateTime.now())
            .actorType(randomEnum(AuditLogActorType.class))
            .actorId("actor-" + generateRandomString(10))
            .tenantId("tenant-" + generateRandomString(10))
            .tenantScope(randomEnum(AuditLogTenantScope.class))
            .result(randomEnum(AuditLogOperationResult.class))
            .annotation("annotation-" + generateRandomString(20))
            .category(randomEnum(AuditLogOperationCategory.class))
            .processDefinitionId("process-" + generateRandomString(10))
            .decisionRequirementsId("decision-req-" + generateRandomString(10))
            .decisionDefinitionId("decision-" + generateRandomString(10))
            .processDefinitionKey(nextKey())
            .processInstanceKey(nextKey())
            .rootProcessInstanceKey(nextKey())
            .elementInstanceKey(nextKey())
            .jobKey(nextKey())
            .userTaskKey(nextKey())
            .decisionRequirementsKey(nextKey())
            .decisionDefinitionKey(nextKey())
            .decisionEvaluationKey(nextKey())
            .deploymentKey(nextKey())
            .formKey(nextKey())
            .resourceKey(nextKey());

    return builderFunction.apply(builder).build();
  }

  public static AuditLogDbModel createRandomProcessInstance() {
    final var builder =
        new AuditLogDbModel.Builder()
            .auditLogKey("audit-" + generateRandomString(15))
            .entityKey(String.valueOf(nextKey()))
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .entityVersion(RANDOM.nextInt(100))
            .entityValueType((short) RANDOM.nextInt(10))
            .entityOperationIntent((short) RANDOM.nextInt(10))
            .batchOperationKey(nextKey())
            .batchOperationType(randomEnum(BatchOperationType.class))
            .timestamp(OffsetDateTime.now())
            .actorType(randomEnum(AuditLogActorType.class))
            .actorId("actor-" + generateRandomString(10))
            .tenantId("tenant-" + generateRandomString(10))
            .tenantScope(AuditLogTenantScope.TENANT)
            .result(randomEnum(AuditLogOperationResult.class))
            .annotation("annotation-" + generateRandomString(20))
            .category(randomEnum(AuditLogOperationCategory.class))
            .processDefinitionId("process-" + generateRandomString(10))
            .decisionRequirementsId("decision-req-" + generateRandomString(10))
            .decisionDefinitionId("decision-" + generateRandomString(10))
            .processDefinitionKey(nextKey())
            .processInstanceKey(nextKey())
            .elementInstanceKey(nextKey())
            .jobKey(nextKey())
            .userTaskKey(nextKey())
            .decisionRequirementsKey(nextKey())
            .decisionDefinitionKey(nextKey())
            .decisionEvaluationKey(nextKey())
            .deploymentKey(nextKey())
            .formKey(nextKey())
            .resourceKey(nextKey());

    return builder.build();
  }

  public static void createAndSaveRandomAuditLogs(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomAuditLogs(
      final RdbmsWriters rdbmsWriters,
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    createAndSaveRandomAuditLogs(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomAuditLogs(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getAuditLogWriter().create(AuditLogFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static AuditLogDbModel createAndSaveAuditLog(
      final RdbmsWriters rdbmsWriters,
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    final AuditLogDbModel randomized = createRandomized(builderFunction);
    createAndSaveAuditLogs(rdbmsWriters, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveAuditLog(
      final RdbmsWriters rdbmsWriters, final AuditLogDbModel auditLog) {
    createAndSaveAuditLogs(rdbmsWriters, List.of(auditLog));
  }

  public static void createAndSaveAuditLogs(
      final RdbmsWriters rdbmsWriters, final List<AuditLogDbModel> auditLogList) {
    for (final AuditLogDbModel auditLog : auditLogList) {
      rdbmsWriters.getAuditLogWriter().create(auditLog);
    }
    rdbmsWriters.flush();
  }
}
