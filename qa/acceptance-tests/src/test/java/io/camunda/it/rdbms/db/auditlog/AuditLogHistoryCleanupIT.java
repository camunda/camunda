/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.auditlog;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryConfig;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.security.reader.ResourceAccessChecks;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuditLogHistoryCleanupIT {

  @TestTemplate
  public void shouldCleanupAuditLogOperations(final CamundaRdbmsTestApplication testApplication) {
    // GIVEN
    final var rdbmsService = testApplication.getRdbmsService();
    final var config = new RdbmsWriterConfig.Builder().partitionId(0).build();
    final var rdbmsWriter = rdbmsService.createWriter(config);
    final var historyCleanupService =
        new HistoryCleanupService(config, rdbmsWriter, rdbmsService.getProcessInstanceReader());
    final var auditLogReader = rdbmsService.getAuditLogReader();

    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime scheduledCleanupDate = now.plus(HistoryConfig.DEFAULT_HISTORY_TTL);
    final var auditLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriter,
            b -> b.entityType(AuditLogEntityType.GROUP).historyCleanupDate(scheduledCleanupDate));

    // WHEN we do the history cleanup (partition doesn't matter here)
    final OffsetDateTime cleanupDate = scheduledCleanupDate.plusSeconds(1);
    historyCleanupService.cleanupHistory(0, cleanupDate);

    // THEN
    assertThat(auditLogReader.getById(auditLog.auditLogKey(), ResourceAccessChecks.disabled()))
        .isNull();
  }

  @TestTemplate
  public void shouldNotCleanupAuditLogOperations(
      final CamundaRdbmsTestApplication testApplication) {
    // GIVEN
    final var rdbmsService = testApplication.getRdbmsService();
    final var config = new RdbmsWriterConfig.Builder().partitionId(0).build();
    final var rdbmsWriter = rdbmsService.createWriter(config);
    final var historyCleanupService =
        new HistoryCleanupService(config, rdbmsWriter, rdbmsService.getProcessInstanceReader());
    final var auditLogReader = rdbmsService.getAuditLogReader();

    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime scheduledCleanupDate = now.plus(HistoryConfig.DEFAULT_HISTORY_TTL);
    final var auditLog =
        AuditLogFixtures.createAndSaveAuditLog(
            rdbmsWriter,
            b -> b.entityType(AuditLogEntityType.GROUP).historyCleanupDate(scheduledCleanupDate));

    // AND we schedule history cleanup
    final OffsetDateTime cleanupDate = OffsetDateTime.now();
    rdbmsWriter.flush();

    // WHEN we do the history cleanup too early (partition doesn't matter here)
    historyCleanupService.cleanupHistory(0, cleanupDate);

    // THEN
    assertThat(auditLogReader.getById(auditLog.auditLogKey(), ResourceAccessChecks.disabled()))
        .isNotNull();
  }
}
