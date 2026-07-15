/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.BranchCorrelation;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The branch-correlation read: for a deployed model's decision gateways, which declared driver
 * variable value most predicts reaching a given branch target (by lift), restricted to real gateway
 * outgoing targets so pass-through elements never pollute the ranking.
 */
final class DashboardBranchCorrelationServingTest {

  private static final String PROCESS = "claim-process";

  private Fixture fixture;
  private DashboardRepository repository;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
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
  void shouldRecoverTheGatewaysConditionInputAsTheTopDriverWithNearPerfectLift() {
    // given a deployed model with one decision gateway (capture / declined) and a route variable
    // that IS the gateway's condition input: route=manual always reaches "declined", route=auto
    // always reaches "capture" — the decision rule itself
    seedDefinition(77L, modelXml());
    seedBranchRoute("capture", "auto", 40);
    seedBranchRoute("declined", "manual", 40);
    // and a pass-through element carrying the same variable, which must never appear as a target
    seedBranchRoute("passthrough", "auto", 40);

    // when the correlation reads
    final List<BranchCorrelation> correlations = repository.branchCorrelations(PROCESS, null, null);

    // then each real branch target recovers its perfect driver with lift ~2 (each branch is half
    // the restricted population, so P(target|its value) = 1 and P(target) = 0.5 -> lift = 2)
    assertThat(correlations).hasSize(2);
    final BranchCorrelation capture =
        correlations.stream().filter(c -> c.targetId().equals("capture")).findFirst().orElseThrow();
    assertThat(capture.gatewayId()).isEqualTo("decision");
    assertThat(capture.variable()).isEqualTo("route");
    assertThat(capture.value()).isEqualTo("auto");
    assertThat(capture.lift()).isCloseTo(2.0, within(0.05));
    final BranchCorrelation declined =
        correlations.stream()
            .filter(c -> c.targetId().equals("declined"))
            .findFirst()
            .orElseThrow();
    assertThat(declined.variable()).isEqualTo("route");
    assertThat(declined.value()).isEqualTo("manual");
    assertThat(declined.lift()).isCloseTo(2.0, within(0.05));
  }

  @Test
  void shouldExcludeAPairBelowTheMinimumSupport() {
    // given one branch target with fewer than MIN_SUPPORT (10) joint observations
    seedDefinition(77L, modelXml());
    seedBranchRoute("capture", "auto", 5);

    // when read
    final List<BranchCorrelation> correlations = repository.branchCorrelations(PROCESS, null, null);

    // then the sparse target never appears
    assertThat(correlations).isEmpty();
  }

  @Test
  void shouldServeEmptyWhenThereIsNoBranchCorrCube() {
    // given a catalog with no corr-branch-* cube provisioned
    final CompiledDataset elements = fixture.catalog().require("elements");
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    byName.put(elements.name(), elements);
    final DashboardRepository withoutCorrCubes =
        new DashboardRepository(
            fixture.executor(),
            new DatasetCatalog(byName),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
    seedDefinition(77L, modelXml());

    // when / then — never a 500, just an empty read
    assertThat(withoutCorrCubes.branchCorrelations(PROCESS, null, null)).isEmpty();
  }

  @Test
  void shouldServeEmptyWhenNoDefinitionWasObserved() {
    // given no process-definitions row for the process
    assertThat(repository.branchCorrelations(PROCESS, null, null)).isEmpty();
  }

  /** start -> authorize -> decision (2-out XOR: capture / declined) -> passthrough (1-out XOR). */
  private static String modelXml() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent("started")
            .serviceTask("authorize", t -> t.zeebeJobType("authorize"))
            .exclusiveGateway("decision")
            .conditionExpression("=approved")
            .serviceTask("capture", t -> t.zeebeJobType("capture"))
            .exclusiveGateway("passthrough")
            .endEvent("captured")
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

  private void seedBranchRoute(final String elementId, final String route, final int count) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      facts.add(
          Fact.builder(FactType.ELEMENT)
              .transition(Transition.COMPLETED)
              .field("var.route", route)
              .build());
    }
    ServingTestSupport.seed(
        fixture, "corr-branch-route", "count", facts, PROCESS, elementId, route);
  }
}
