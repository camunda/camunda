/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.table.ProcessDefinitionSink;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.dashboard.BranchCorrelation;
import io.camunda.analytics.webapp.dashboard.DashboardController;
import io.camunda.analytics.webapp.dashboard.DashboardRepository;
import io.camunda.analytics.webapp.dashboard.ElementOutlier;
import io.camunda.analytics.webapp.dashboard.VariableCorrelation;
import io.camunda.analytics.webapp.dashboard.VariantCorrelation;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * All four outlier-analysis endpoints (outliers, variable/variant/branch correlation) wire straight
 * through to the repository, and — mirroring the neighboring endpoints' parameter validation — an
 * empty/reversed range is never a 500, just an empty (or query-defined) result.
 */
final class DashboardOutliersControllerTest {

  private static final String PROCESS = "claim-process";

  private Fixture fixture;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    fixture = ServingTestSupport.create();
    fixture.datasetStore().schemaManager().ensureTable(ProcessDefinitionSink.TABLE);
    final DashboardRepository repository =
        new DashboardRepository(
            fixture.executor(),
            fixture.catalog(),
            fixture.tableCatalog(),
            new TableQueryExecutor(fixture.datasetStore().queryClient()),
            new SnapshotQueryExecutor(fixture.datasetStore().queryClient()));
    controller = new DashboardController(repository);
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldServeOutliersForAProcessWithEnoughObservations() {
    // given a hot element with a skewed distribution well above MIN_OBSERVATIONS
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      facts.add(elementCompleted(900L + i * 4L));
    }
    for (int i = 0; i < 5; i++) {
      facts.add(elementCompleted(100_000L));
    }
    ServingTestSupport.seed(fixture, "elements", "duration_p", facts, PROCESS, "worker");

    // when the endpoint is called directly (mirrors the repository read)
    final List<ElementOutlier> outliers = controller.outliers(PROCESS, null, null);

    // then it serves the same row the repository would
    assertThat(outliers)
        .singleElement()
        .satisfies(o -> assertThat(o.elementId()).isEqualTo("worker"));
  }

  @Test
  void shouldServeAnEmptyOutlierListForAnUnknownProcess() {
    assertThat(controller.outliers("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeLikeEveryNeighboringEndpoint() {
    // given / when / then — an inverted [from, to) is rejected at the query layer for every
    // dashboard endpoint (ReportQuery), turned into a 400 by the controller's exception handler;
    // this endpoint does not invent different behavior
    assertThatThrownBy(() -> controller.outliers(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldServeVariableCorrelationForAProcessWithOutliers() {
    // given an overall outlier-bearing population and a heavily-biased corr-route value
    final List<Fact> overall = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      overall.add(completed(900L + i * 2L));
    }
    for (int i = 0; i < 10; i++) {
      overall.add(completed(50_000L));
    }
    ServingTestSupport.seed(fixture, "process-duration", "percentiles", overall, PROCESS);
    final List<Fact> manual = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      manual.add(routeCompleted(49_000L + i * 100L, "manual"));
    }
    ServingTestSupport.seed(fixture, "corr-route", "duration_p", manual, PROCESS, "manual");

    // when the endpoint is called directly
    final List<VariableCorrelation> correlations =
        controller.variableCorrelation(PROCESS, null, null);

    // then it serves the biased value with lift above 1
    assertThat(correlations).isNotEmpty();
    assertThat(correlations.get(0).lift()).isGreaterThan(1.0);
  }

  @Test
  void shouldServeAnEmptyCorrelationListForAnUnknownProcess() {
    assertThat(controller.variableCorrelation("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeOnCorrelationTooLikeEveryNeighboringEndpoint() {
    assertThatThrownBy(() -> controller.variableCorrelation(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldServeVariantCorrelationForAProcessWithVariants() {
    // given joint counts biasing variant 111 toward route=auto
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("variantHash", 111L)
              .field("var.route", "auto")
              .build());
    }
    ServingTestSupport.seed(fixture, "corr-variant-route", "count", facts, PROCESS, 111L, "auto");

    // when the endpoint is called directly
    final List<VariantCorrelation> correlations =
        controller.variantCorrelation(PROCESS, null, null);

    // then it serves the variant's top driver
    assertThat(correlations)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.variantHash()).isEqualTo("111");
              assertThat(c.value()).isEqualTo("auto");
            });
  }

  @Test
  void shouldServeAnEmptyVariantCorrelationListForAnUnknownProcess() {
    assertThat(controller.variantCorrelation("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeOnVariantCorrelationLikeEveryNeighboringEndpoint() {
    // same ReportQuery-level rejection as every other range-taking endpoint (mirror, don't invent)
    assertThatThrownBy(() -> controller.variantCorrelation(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldServeBranchCorrelationForADeployedModelWithADecisionGateway() {
    // given a deployed model with one decision gateway and a route-driven branch
    seedDefinition(77L, modelXml());
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      facts.add(
          Fact.builder(FactType.ELEMENT)
              .transition(Transition.COMPLETED)
              .field("var.route", "auto")
              .build());
    }
    ServingTestSupport.seed(
        fixture, "corr-branch-route", "count", facts, PROCESS, "capture", "auto");

    // when the endpoint is called directly
    final List<BranchCorrelation> correlations = controller.branchCorrelation(PROCESS, null, null);

    // then it serves the branch's top driver
    assertThat(correlations)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.gatewayId()).isEqualTo("decision");
              assertThat(c.targetId()).isEqualTo("capture");
              assertThat(c.value()).isEqualTo("auto");
            });
  }

  @Test
  void shouldServeAnEmptyBranchCorrelationListForAnUnknownProcess() {
    assertThat(controller.branchCorrelation("unknown", null, null)).isEmpty();
  }

  @Test
  void shouldRejectAReversedRangeOnBranchCorrelationLikeEveryNeighboringEndpoint() {
    // given a deployed model (so the read reaches the range-bound query rather than short-circuit
    // on "no definition")
    seedDefinition(77L, modelXml());
    ServingTestSupport.seed(
        fixture, "corr-branch-route", "count", List.of(), PROCESS, "capture", "auto");

    // when / then — same ReportQuery-level rejection as every other range-taking endpoint
    assertThatThrownBy(() -> controller.branchCorrelation(PROCESS, 10_000L, 5_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** start -> authorize -> decision (2-out XOR: capture / declined). */
  private static String modelXml() {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS)
            .startEvent("started")
            .serviceTask("authorize", t -> t.zeebeJobType("authorize"))
            .exclusiveGateway("decision")
            .conditionExpression("=approved")
            .serviceTask("capture", t -> t.zeebeJobType("capture"))
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

  private static Fact completed(final long durationMs) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }

  private static Fact routeCompleted(final long durationMs, final String route) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .field("var.route", route)
        .build();
  }

  private static Fact elementCompleted(final long durationMs) {
    return Fact.builder(FactType.ELEMENT)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }
}
