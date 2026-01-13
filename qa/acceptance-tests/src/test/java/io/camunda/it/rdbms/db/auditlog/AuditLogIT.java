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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.it.rdbms.db.fixtures.AuditLogFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.sort.AuditLogSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuditLogIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldSaveAndFindAuditLogByEntityKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader auditLogReader = rdbmsService.getAuditLogReader();

    final var original = AuditLogFixtures.createRandomized(b -> b);
    createAndSaveAuditLog(rdbmsWriters, original);

    final var instance = auditLogReader.findByEntityKey(original.entityKey()).orElse(null);

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
}
