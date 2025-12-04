/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
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

  public static void createAndSaveRandomAuditLogs(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomAuditLogs(rdbmsWriter, b -> b);
  }

  public static void createAndSaveRandomAuditLogs(
      final RdbmsWriter rdbmsWriter,
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getAuditLogWriter().create(AuditLogFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static AuditLogDbModel createAndSaveAuditLog(
      final RdbmsWriter rdbmsWriter,
      final Function<AuditLogDbModel.Builder, AuditLogDbModel.Builder> builderFunction) {
    final AuditLogDbModel randomized = createRandomized(builderFunction);
    createAndSaveAuditLogs(rdbmsWriter, List.of(randomized));
    return randomized;
  }

  public static void createAndSaveAuditLog(
      final RdbmsWriter rdbmsWriter, final AuditLogDbModel auditLog) {
    createAndSaveAuditLogs(rdbmsWriter, List.of(auditLog));
  }

  public static void createAndSaveAuditLogs(
      final RdbmsWriter rdbmsWriter, final List<AuditLogDbModel> auditLogList) {
    for (final AuditLogDbModel auditLog : auditLogList) {
      rdbmsWriter.getAuditLogWriter().create(auditLog);
    }
    rdbmsWriter.flush();
  }
}
