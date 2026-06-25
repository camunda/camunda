/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.auditlog;

import static io.camunda.it.rdbms.db.fixtures.AuditLogFixtures.createAndSaveRandomAuditLogs;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.it.rdbms.db.util.RdbmsTestTemplate;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.sort.AuditLogSort;
import io.camunda.search.sort.AuditLogSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class AuditLogSortIT {

  public static final long PARTITION_ID = 0L;

  @RdbmsTestTemplate
  public void shouldSortByAuditLogKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.auditLogKey().asc(),
        Comparator.comparing(AuditLogEntity::auditLogKey));
  }

  @RdbmsTestTemplate
  public void shouldSortByAuditLogKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.auditLogKey().desc(),
        Comparator.comparing(AuditLogEntity::auditLogKey).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByTimestampAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.timestamp().asc(),
        Comparator.comparing(AuditLogEntity::timestamp));
  }

  @RdbmsTestTemplate
  public void shouldSortByTimestampDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.timestamp().desc(),
        Comparator.comparing(AuditLogEntity::timestamp).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByActorIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.actorId().asc(),
        Comparator.comparing(AuditLogEntity::actorId));
  }

  @RdbmsTestTemplate
  public void shouldSortByActorIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.actorId().desc(),
        Comparator.comparing(AuditLogEntity::actorId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(AuditLogEntity::tenantId));
  }

  @RdbmsTestTemplate
  public void shouldSortByTenantIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().desc(),
        Comparator.comparing(AuditLogEntity::tenantId).reversed());
  }

  @RdbmsTestTemplate
  public void shouldSortByEntityTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.entityType().asc(),
        Comparator.comparing(e -> e.entityType().name()));
  }

  @RdbmsTestTemplate
  public void shouldSortByEntityTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.entityType().desc(),
        Comparator.<AuditLogEntity, String>comparing(e -> e.entityType().name()).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<AuditLogSort>> sortBuilder,
      final Comparator<AuditLogEntity> comparator) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final AuditLogDbReader reader = rdbmsService.getAuditLogReader();

    final var actorId = nextStringId();
    createAndSaveRandomAuditLogs(rdbmsWriters, b -> b.actorId(actorId));

    final var searchResult =
        reader
            .search(
                new AuditLogQuery(
                    new AuditLogFilter.Builder().actorIds(actorId).build(),
                    AuditLogSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
