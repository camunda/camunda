/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.AuthorizationFixtures;
import io.camunda.it.rdbms.db.fixtures.IncidentFixtures;
import io.camunda.it.rdbms.db.fixtures.JobMetricsBatchFixtures;
import io.camunda.it.rdbms.db.fixtures.MessageSubscriptionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.UserTaskFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Regression guard for backward keyset pagination on RDBMS storage.
 *
 * <p>A {@code before} page has to be seeked against the display direction, otherwise the page-size
 * LIMIT keeps the first rows of the whole preceding range instead of the rows next to the cursor,
 * and every backward step lands on page 1 (#61712). The reversal lives in the shared {@code
 * Commons.orderBy} fragment and in {@code AbstractEntityReader}, so it is easy to bypass by
 * accident: a mapper that hardcodes its ORDER BY, or a reader that assembles its result without
 * {@code executePagedQuery}, silently keeps the old behaviour.
 *
 * <p>The scenarios below therefore cover one reader per statement shape that the shared keyset SQL
 * has to serve, rather than one per entity. Add a scenario whenever a new shape appears.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class KeySetBackwardPagingIT {

  private static final int PARTITION_ID = 0;
  private static final int PAGE_SIZE = 3;
  private static final int SEEDED_ENTRIES = 10;

  @TestTemplate
  public void shouldReturnTheForwardPagesWhenPagingBackwards(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);

    final var scenarios =
        List.of(
            incidentSearch(rdbmsService, rdbmsWriters),
            processInstanceSearch(rdbmsService, rdbmsWriters),
            authorizationSearch(rdbmsService, rdbmsWriters),
            userTaskSearch(rdbmsService),
            jobTypeStatistics(rdbmsService, rdbmsWriters),
            messageSubscriptionStatistics(rdbmsService, rdbmsWriters));

    // when / then
    final var softly = new SoftAssertions();
    scenarios.forEach(scenario -> assertBackwardPagingRepeatsForwardPaging(softly, scenario));
    softly.assertAll();
  }

  /**
   * Walks three pages forwards, then walks the same cursors back, and asserts that each backward
   * page carries the entries — in the same order — that the forward traversal produced for it.
   */
  private static void assertBackwardPagingRepeatsForwardPaging(
      final SoftAssertions softly, final Scenario scenario) {
    final var search = scenario.search();

    final var firstPage = search.execute(p -> p.size(PAGE_SIZE));
    final var secondPage = search.execute(p -> p.size(PAGE_SIZE).after(firstPage.endCursor()));
    final var thirdPage = search.execute(p -> p.size(PAGE_SIZE).after(secondPage.endCursor()));

    final var backToSecondPage =
        search.execute(p -> p.size(PAGE_SIZE).before(thirdPage.startCursor()));
    final var backToFirstPage =
        search.execute(p -> p.size(PAGE_SIZE).before(backToSecondPage.startCursor()));

    softly
        .assertThat(List.of(firstPage.items(), secondPage.items(), thirdPage.items()))
        .as("%s: the seeded entries fill three forward pages", scenario.name())
        .allMatch(items -> items.size() == PAGE_SIZE);
    softly
        .assertThat(backToSecondPage.items())
        .as("%s: paging back from page 3 returns page 2", scenario.name())
        .isEqualTo(secondPage.items());
    softly
        .assertThat(backToFirstPage.items())
        .as("%s: paging back from page 2 returns page 1", scenario.name())
        .isEqualTo(firstPage.items());
  }

  /** Flat statement: keyset filter, ORDER BY and LIMIT all applied to one subquery. */
  private static Scenario incidentSearch(
      final RdbmsService rdbmsService, final RdbmsWriters rdbmsWriters) {
    final var processDefinitionKey = nextKey();
    IncidentFixtures.createAndSaveRandomIncidents(
        rdbmsWriters, SEEDED_ENTRIES, b -> b.processDefinitionKey(processDefinitionKey));

    final var reader = rdbmsService.getIncidentReader();
    return new Scenario(
        "incident search",
        page ->
            reader.search(
                IncidentQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                            .sort(s -> s.state().asc().creationTime().desc())
                            .page(page))));
  }

  /** Seek subquery plus two outer re-sorts around a tag join. */
  private static Scenario processInstanceSearch(
      final RdbmsService rdbmsService, final RdbmsWriters rdbmsWriters) {
    final var processDefinition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    ProcessInstanceFixtures.createAndSaveRandomProcessInstances(
        rdbmsWriters,
        SEEDED_ENTRIES,
        b ->
            b.processDefinitionKey(processDefinition.processDefinitionKey())
                .processDefinitionId(processDefinition.processDefinitionId()));

    final var reader = rdbmsService.getProcessInstanceReader();
    return new Scenario(
        "process instance search",
        page ->
            reader.search(
                ProcessInstanceQuery.of(
                    b ->
                        b.filter(
                                f ->
                                    f.processDefinitionIds(processDefinition.processDefinitionId()))
                            .sort(s -> s.startDate().desc())
                            .page(page))));
  }

  /** Seek subquery over a DISTINCT projection plus one outer re-sort after the permission join. */
  private static Scenario authorizationSearch(
      final RdbmsService rdbmsService, final RdbmsWriters rdbmsWriters) {
    final var ownerId = nextStringId();
    AuthorizationFixtures.createAndSaveRandomAuthorizations(
        rdbmsWriters, SEEDED_ENTRIES, b -> b.ownerId(ownerId));

    final var reader = rdbmsService.getAuthorizationReader();
    return new Scenario(
        "authorization search",
        page ->
            reader.search(
                AuthorizationQuery.of(
                    b ->
                        b.filter(f -> f.ownerIds(ownerId))
                            .sort(s -> s.resourceId().desc())
                            .page(page))));
  }

  /** Sort on a low-cardinality column, so the cursor leans on the unique key discriminator. */
  private static Scenario userTaskSearch(final RdbmsService rdbmsService) {
    final var processDefinitionId = nextStringId();
    UserTaskFixtures.createAndSaveRandomUserTasks(
        rdbmsService, SEEDED_ENTRIES, b -> b.processDefinitionId(processDefinitionId));

    final var reader = rdbmsService.getUserTaskReader();
    return new Scenario(
        "user task search",
        page ->
            reader.search(
                UserTaskQuery.of(
                    b ->
                        b.filter(f -> f.processDefinitionIds(processDefinitionId))
                            .sort(s -> s.priority().desc())
                            .page(page))));
  }

  /** Aggregation: GROUP BY, and a reader that builds its result without executePagedQuery. */
  private static Scenario jobTypeStatistics(
      final RdbmsService rdbmsService, final RdbmsWriters rdbmsWriters) {
    // a window five years back keeps the metrics other tests write out of the aggregation
    final var windowStart =
        OffsetDateTime.now(ZoneOffset.UTC).minusYears(5).truncatedTo(ChronoUnit.MILLIS);
    final var jobTypePrefix = "job-type-" + nextStringId();
    for (int i = 0; i < SEEDED_ENTRIES; i++) {
      final var index = i;
      JobMetricsBatchFixtures.createAndSaveMetric(
          rdbmsWriters,
          JobMetricsBatchFixtures.createRandomized(
              b ->
                  b.jobType("%s-%02d".formatted(jobTypePrefix, index))
                      .startTime(windowStart.plusMinutes(index))
                      .endTime(windowStart.plusMinutes(index + 1L))));
    }

    final var reader = rdbmsService.getJobMetricsBatchDbReader();
    return new Scenario(
        "job type statistics",
        page ->
            reader.getJobTypeStatistics(
                JobTypeStatisticsQuery.of(
                    b ->
                        b.filter(
                                f ->
                                    f.from(windowStart.minusMinutes(1))
                                        .to(windowStart.plusMinutes(SEEDED_ENTRIES + 1L)))
                            .page(page)),
                ResourceAccessChecks.disabled()));
  }

  /** Aggregation with a hardcoded ORDER BY, which the shared orderBy fragment does not reach. */
  private static Scenario messageSubscriptionStatistics(
      final RdbmsService rdbmsService, final RdbmsWriters rdbmsWriters) {
    // the statistics group by process definition, so every entry needs its own definition
    final var messageName = "message-" + nextStringId();
    for (int i = 0; i < SEEDED_ENTRIES; i++) {
      final var processDefinitionKey = nextKey();
      MessageSubscriptionFixtures.createAndSaveMessageSubscription(
          rdbmsWriters,
          b ->
              b.messageName(messageName)
                  .processDefinitionKey(processDefinitionKey)
                  .processDefinitionId("process-" + processDefinitionKey));
    }

    final var reader = rdbmsService.getProcessDefinitionMessageSubscriptionStatisticsDbReader();
    return new Scenario(
        "process definition message subscription statistics",
        page ->
            reader.aggregate(
                new ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder()
                    .filter(f -> f.messageNames(messageName))
                    .page(page)
                    .build(),
                ResourceAccessChecks.disabled()));
  }

  private record Scenario(String name, PagedSearch search) {}

  @FunctionalInterface
  private interface PagedSearch {
    SearchQueryResult<?> execute(
        Function<SearchQueryPage.Builder, ObjectBuilder<SearchQueryPage>> page);
  }
}
