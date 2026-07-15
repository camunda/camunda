/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.BranchDistribution;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The gateway branch distribution: the deployed model's <em>exclusive</em> gateways (parsed from
 * the process-definitions table's BPMN XML) joined with the elements cube's activation counts.
 * Parallel gateways and single-outgoing (merge) exclusive gateways are not decisions and must be
 * skipped.
 */
final class DashboardBranchDistributionTest {

  private static final String PROCESS = "payment-process";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    // The built-in definitions table is normally ensured by Stage 1; do it here for the read test.
    fixture.datasetStore().schemaManager().ensureTable(ProcessDefinitionSink.TABLE);
    repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldServeBranchSharesForExclusiveGatewaysOnly() {
    // given a deployed model with one decision gateway, one pass-through exclusive gateway and
    // one parallel gateway, plus activation counts for the gateway and its targets
    seedDefinition(77L, modelXml());
    seedActivations("decision", 10);
    seedActivations("capture", 7);
    seedActivations("declined", 3);
    seedActivations("dispatch", 7);
    seedActivations("notify", 7);

    // when the branch distribution reads
    final List<BranchDistribution> gateways = repository.branchDistribution(PROCESS, null, null);

    // then only the two-outgoing exclusive gateway appears — the parallel fan-out takes every
    // branch (not a decision) and the pass-through has nothing to split
    assertThat(gateways)
        .singleElement()
        .satisfies(
            gateway -> {
              assertThat(gateway.gatewayId()).isEqualTo("decision");
              assertThat(gateway.activations()).isEqualTo(10L);
              assertThat(gateway.branches())
                  .extracting(
                      BranchDistribution.Branch::targetId,
                      BranchDistribution.Branch::activations,
                      BranchDistribution.Branch::share)
                  .containsExactlyInAnyOrder(tuple("capture", 7L, 0.7), tuple("declined", 3L, 0.3));
            });
  }

  @Test
  void shouldListAnUnexecutedBranchWithZeroShare() {
    // given every decision so far took the capture branch
    seedDefinition(77L, modelXml());
    seedActivations("decision", 5);
    seedActivations("capture", 5);

    // when the branch distribution reads
    final List<BranchDistribution> gateways = repository.branchDistribution(PROCESS, null, null);

    // then the never-taken branch is still listed, at zero — absence of data is not absence of
    // the branch
    assertThat(gateways.get(0).branches())
        .extracting(BranchDistribution.Branch::targetId, BranchDistribution.Branch::share)
        .containsExactlyInAnyOrder(tuple("capture", 1.0), tuple("declined", 0.0));
  }

  @Test
  void shouldServeZeroSharesWhenTheGatewayNeverActivated() {
    // given a deployed model but no element activity at all in range
    seedDefinition(77L, modelXml());

    // when the branch distribution reads
    final List<BranchDistribution> gateways = repository.branchDistribution(PROCESS, null, null);

    // then the gateway is listed with zero activations and zero shares (no division by zero)
    assertThat(gateways)
        .singleElement()
        .satisfies(
            gateway -> {
              assertThat(gateway.activations()).isZero();
              assertThat(gateway.branches())
                  .extracting(BranchDistribution.Branch::share)
                  .containsExactly(0.0, 0.0);
            });
  }

  @Test
  void shouldServeEmptyWhenNoDefinitionWasObserved() {
    // given no process-definitions row for the process

    // when / then — nothing to parse, nothing to serve
    assertThat(repository.branchDistribution(PROCESS, null, null)).isEmpty();
  }

  /**
   * start → authorize → passthrough (1-out XOR) → decision (2-out XOR) → capture → fanout
   * (parallel: dispatch ∥ notify) / declined.
   */
  private static String modelXml() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent("started")
            .serviceTask("authorize", t -> t.zeebeJobType("authorize"))
            .exclusiveGateway("passthrough")
            .exclusiveGateway("decision")
            .conditionExpression("=approved")
            .serviceTask("capture", t -> t.zeebeJobType("capture"))
            .parallelGateway("fanout")
            .serviceTask("dispatch", t -> t.zeebeJobType("dispatch"))
            .endEvent("shipped")
            .moveToNode("fanout")
            .serviceTask("notify", t -> t.zeebeJobType("notify"))
            .endEvent("notified")
            .moveToNode("decision")
            .conditionExpression("=not(approved)")
            .endEvent("declined")
            .done();
    return Bpmn.convertToString(model);
  }

  private void seedDefinition(final long processDefinitionKey, final String xml) {
    fixture
        .datasetStore()
        .writer()
        .upsertRow(
            ProcessDefinitionSink.TABLE,
            String.valueOf(processDefinitionKey),
            List.of(PROCESS, processDefinitionKey, 1, "<default>", xml),
            WriteVersion.SEED);
    fixture.datasetStore().writer().flush();
  }

  private void seedActivations(final String elementId, final int count) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      facts.add(
          Fact.builder(FactType.ELEMENT)
              .transition(Transition.ACTIVATED)
              .field("processInstanceKey", (long) i)
              .build());
    }
    ServingTestSupport.seed(fixture, "elements", "activations", facts, PROCESS, elementId);
  }
}
