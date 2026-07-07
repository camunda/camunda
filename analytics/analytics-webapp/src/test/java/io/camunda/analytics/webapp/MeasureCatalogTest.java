/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.webapp.MeasureCatalog.QuestionFilter;
import io.camunda.analytics.webapp.MeasureCatalog.QuestionGroupBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MeasureCatalogTest {

  private static final long DAY = 86_400_000L;
  private final MeasureCatalog catalog = new MeasureCatalog();

  @Test
  void shouldExposeEntitiesAndMeasuresInBusinessLanguage() {
    // then the catalog is entity → measure → group-by, all friendly-labelled
    assertThat(catalog.view().entities())
        .extracting(MeasureCatalog.EntityView::id)
        .containsExactly("process-instances", "flow-nodes", "incidents");
    final MeasureCatalog.EntityView instances = catalog.view().entities().get(0);
    assertThat(instances.label()).isEqualTo("Process instances");
    assertThat(instances.measures())
        .extracting(MeasureCatalog.MeasureView::label)
        .contains("Average duration", "% within SLA");
    assertThat(catalog.view().granularities()).isNotEmpty();
    assertThat(catalog.view().visualizations()).contains("line", "bar", "table");
  }

  @Test
  void shouldCompileAnAverageDurationQuestionToADeclaration() {
    // when a plain-language question is compiled
    final MeasureCatalog.CompiledQuestion compiled =
        catalog.compile(
            "process-instances",
            "avg-duration",
            Map.of(),
            List.of(new QuestionGroupBy("bpmnProcessId", false)),
            List.of(),
            DAY);

    // then it maps to the engine model: process-instance fact, the grouping dimension, the summary
    // meter, and the requested granularity as a stored tier
    final DatasetDeclaration declaration = compiled.declaration();
    assertThat(declaration.sourceFact()).isEqualTo(FactType.PROCESS_INSTANCE);
    assertThat(declaration.dimensions()).extracting("name").containsExactly("bpmnProcessId");
    assertThat(declaration.meters())
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.type()).isEqualTo(MeterCatalog.EXECUTION_TIME_SUMMARY);
              assertThat(m.measureField()).isEqualTo("durationMs");
            });
    assertThat(declaration.windowSizesMs()).contains(DAY);
    assertThat(compiled.meterName()).isEqualTo("duration");
  }

  @Test
  void shouldMapThePercentileParamToRanks() {
    // when a percentile question chooses p90
    final Meter meter =
        catalog
            .compile(
                "process-instances",
                "duration-percentile",
                Map.of("percentile", 90.0),
                List.of(),
                List.of(),
                DAY)
            .declaration()
            .meters()
            .get(0);

    // then the friendly percentile becomes the engine's rank fraction
    assertThat(meter.type()).isEqualTo(MeterCatalog.PERCENTILE);
    assertThat(meter.params()).containsEntry("ranks", "0.9");
  }

  @Test
  void shouldMapTheSlaTargetToARatioThreshold() {
    // when an SLA question sets a 2-minute target
    final Meter meter =
        catalog
            .compile(
                "process-instances",
                "sla-compliance",
                Map.of("slaTargetMs", 120_000.0),
                List.of(),
                List.of(),
                DAY)
            .declaration()
            .meters()
            .get(0);

    // then it becomes a ratio meter with a le threshold
    assertThat(meter.type()).isEqualTo(MeterCatalog.RATIO);
    assertThat(meter.params()).containsEntry("op", "le").containsEntry("threshold", "120000");
  }

  @Test
  void shouldDeriveTheSameDatasetNameForTheSameQuestion() {
    // then two identical questions derive the same dataset name (so the cube is reused, not
    // duplicated)
    final String first =
        catalog
            .compile("incidents", "incident-count", Map.of(), List.of(), List.of(), DAY)
            .datasetName();
    final String second =
        catalog
            .compile("incidents", "incident-count", Map.of(), List.of(), List.of(), DAY)
            .datasetName();
    assertThat(first).isEqualTo(second).startsWith("q:incidents:incident-count");
  }

  @Test
  void shouldRejectAnUnknownMeasure() {
    assertThatThrownBy(
            () ->
                catalog.compile(
                    "process-instances", "no-such-measure", Map.of(), List.of(), List.of(), DAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-measure");
  }

  @Test
  void shouldDeriveValidDeclarationsForEveryMeasure() {
    // given the validation-gating compiler a provisioned declaration goes through
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));

    // when every entity/measure question is compiled (grouped, with a variable group-by)
    long cubeId = 1;
    for (final MeasureCatalog.EntityView entity : catalog.view().entities()) {
      for (final MeasureCatalog.MeasureView measure : entity.measures()) {
        final DatasetDeclaration declaration =
            catalog
                .compile(
                    entity.id(),
                    measure.id(),
                    Map.of(),
                    List.of(
                        new QuestionGroupBy("bpmnProcessId", false),
                        new QuestionGroupBy("var.region", true)),
                    List.of(new QuestionFilter("bpmnProcessId", "Invoice")),
                    DAY)
                .declaration();

        // then the derived declaration passes the declaration-time validation gate
        final long id = cubeId++;
        assertThatCode(() -> compiler.compile(id, declaration))
            .as("measure '%s' of entity '%s'", measure.id(), entity.id())
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void shouldKeepDerivedWindowTiersAscending() {
    // when the requested granularity is finer than a measure's coarsest default tier
    // (avg-duration defaults to minute+hour tiers; the minute granularity is already contained)
    final DatasetDeclaration declaration =
        catalog
            .compile("process-instances", "avg-duration", Map.of(), List.of(), List.of(), 60_000L)
            .declaration();

    // then the tiers are ascending (the builder sorts, so tier validation holds)
    assertThat(declaration.windowSizesMs()).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void shouldCompileAWhereFilterIntoTheDataset() {
    final DatasetDeclaration declaration =
        catalog
            .compile(
                "process-instances",
                "instance-count",
                Map.of(),
                List.of(),
                List.of(new QuestionFilter("bpmnProcessId", "Invoice")),
                DAY)
            .declaration();
    assertThat(declaration.filters())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.field()).isEqualTo("bpmnProcessId");
              assertThat(f.value()).isEqualTo("Invoice");
            });
  }
}
