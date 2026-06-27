/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.waitstate;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.WaitStateFixtures.createAndSaveRandomWaitStates;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.WaitStateDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.sort.WaitStateSort;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class WaitStateIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldInsertAndFindWaitStateByKey(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final var model = createRandomized(nextKey());

    // when
    rdbmsWriters.getWaitStateWriter().create(model);
    rdbmsWriters.flush();

    // then
    final var found = rdbmsService.getWaitStateReader().findOne(model.waitStateKey()).orElse(null);
    assertThat(found).isNotNull();
    assertThat(found.waitStateKey()).isEqualTo(model.waitStateKey());
    assertThat(found.rootProcessInstanceKey()).isEqualTo(model.rootProcessInstanceKey());
    assertThat(found.processInstanceKey()).isEqualTo(model.processInstanceKey());
    assertThat(found.elementInstanceKey()).isEqualTo(model.elementInstanceKey());
    assertThat(found.elementId()).isEqualTo(model.elementId());
    assertThat(found.elementType()).isEqualTo(model.elementType());
    assertThat(found.waitStateType()).isEqualTo(model.waitStateType());
    assertThat(found.details()).isEqualTo(model.details());
    assertThat(found.tenantId()).isEqualTo(model.tenantId());
    assertThat(found.partitionId()).isEqualTo(PARTITION_ID);
  }

  @TestTemplate
  public void shouldDeleteWaitStateByKey(final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final var model = createRandomized(nextKey());
    rdbmsWriters.getWaitStateWriter().create(model);
    rdbmsWriters.flush();
    assertThat(rdbmsService.getWaitStateReader().findOne(model.waitStateKey())).isPresent();

    // when
    rdbmsWriters.getWaitStateWriter().delete(model.waitStateKey());
    rdbmsWriters.flush();

    // then
    assertThat(rdbmsService.getWaitStateReader().findOne(model.waitStateKey())).isEmpty();
  }

  @TestTemplate
  public void shouldReturnCorrectResultsWithKeysetPagination(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final var processInstanceKey = nextKey();
    createAndSaveRandomWaitStates(
        rdbmsWriters,
        b -> b.processInstanceKey(processInstanceKey).rootProcessInstanceKey(nextKey()));

    final WaitStateDbReader reader = rdbmsService.getWaitStateReader();
    final var sort = WaitStateSort.of(s -> s.elementInstanceKey().asc());
    final var filter =
        new ElementInstanceWaitStateFilter.Builder()
            .processInstanceKeys(processInstanceKey)
            .build();

    // when
    final var allItems =
        reader
            .search(
                new ElementInstanceWaitStateQuery(
                    filter, sort, SearchQueryPage.of(b -> b.from(0).size(20))),
                ResourceAccessChecks.disabled())
            .items();

    final var page1 =
        reader.search(
            new ElementInstanceWaitStateQuery(filter, sort, SearchQueryPage.of(b -> b.size(10))),
            ResourceAccessChecks.disabled());

    final var page2 =
        reader.search(
            new ElementInstanceWaitStateQuery(
                filter, sort, SearchQueryPage.of(b -> b.size(10).after(page1.endCursor()))),
            ResourceAccessChecks.disabled());

    // then
    assertThat(allItems).hasSize(20);
    assertThat(page1.items()).hasSize(10);
    assertThat(page2.items()).hasSize(10);
    assertThat(page1.items()).isEqualTo(allItems.subList(0, 10));
    assertThat(page2.items()).isEqualTo(allItems.subList(10, 20));
    // total() must be 20 on page 2 — not the post-cursor remainder.
    // Guards against the keyset predicate leaking into the COUNT query.
    assertThat(page2.total()).isEqualTo(20);
  }

  private static WaitStateDbModel createRandomized(final long waitStateKey) {
    return new WaitStateDbModel.Builder()
        .waitStateKey(waitStateKey)
        .rootProcessInstanceKey(nextKey())
        .processInstanceKey(nextKey())
        .elementInstanceKey(nextKey())
        .elementId("task-" + waitStateKey)
        .elementType(BpmnElementType.SERVICE_TASK.name())
        .waitStateType("JOB")
        .details("{\"jobKey\":" + waitStateKey + ",\"jobType\":\"payment\",\"retries\":3}")
        .tenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .partitionId(PARTITION_ID)
        .build();
  }
}
