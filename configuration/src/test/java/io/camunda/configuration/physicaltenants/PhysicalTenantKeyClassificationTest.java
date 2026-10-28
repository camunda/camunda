/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.physicaltenants.PhysicalTenantKeyClassification.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PhysicalTenantKeyClassificationTest {

  private static final Map<String, String> CURRENT =
      Map.of(
          "name", "Tenant",
          "data.secondary-storage.type", "elasticsearch",
          "data.secondary-storage.elasticsearch.url", "http://es:9200",
          "data.exporters.camundaexporter.class-name", "io.camunda.exporter.CamundaExporter",
          "data.exporters.camundaexporter.args.bulk.size", "100",
          "data.snapshot-period", "5m");

  private final PhysicalTenantKeyClassification classification =
      PhysicalTenantKeyClassification.load();

  @Test
  void shouldClassifyEveryOverridableProperty() throws Exception {
    // given
    final var golden =
        Files.readAllLines(
                Path.of(
                    PhysicalTenantKeyClassificationTest.class
                        .getResource("overridable-properties.golden.txt")
                        .toURI()),
                StandardCharsets.UTF_8)
            .stream()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();

    // when
    final var classified = classification.classifiedProperties();

    // then
    assertThat(classified)
        .as(
            "Every overridable physical-tenant property needs a runtime classification in %s",
            PhysicalTenantKeyClassification.RESOURCE)
        .containsExactlyInAnyOrderElementsOf(golden);
  }

  @Test
  void shouldAcceptAChangeAppliedByARestart() {
    // given
    final var proposed = with("data.snapshot-period", "7m");
    proposed.put("name", "Renamed");
    proposed.put("data.exporters.camundaexporter.args.bulk.size", "500");

    // when
    final var violations = classification.validate(update(proposed));

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldRejectAChangeOfTheSecondaryStorageLocation() {
    // when
    final var violations =
        classification.validate(update(with("data.secondary-storage.elasticsearch.url", "x")));

    // then
    assertThat(violations)
        .singleElement()
        .asString()
        .contains("data.secondary-storage.elasticsearch.url", "storage-location");
  }

  @Test
  void shouldRejectAChangeOfTheExporterIds() {
    // given
    final var proposed = with("data.exporters.other.class-name", "io.camunda.Other");

    // when
    final var violations = classification.validate(update(proposed));

    // then
    assertThat(violations)
        .anySatisfy(violation -> assertThat(violation).contains("exporters", "other"));
  }

  @Test
  void shouldRejectAPropertyWithoutClassification() {
    // when
    final var violations = classification.validate(update(with("data.made-up", "1")));

    // then
    assertThat(violations).singleElement().asString().contains("no runtime classification");
  }

  @Test
  void shouldClassifyANestedNameByThePropertyItBelongsTo() {
    // when
    final var exporterArgument = classification.classify("data.exporters.foo.args.bar[0]");
    final var listElement =
        classification.classify("security.initialization.default-roles.admin.users[0]");

    // then
    assertThat(exporterArgument)
        .map(PhysicalTenantKeyClassification.Classification::kind)
        .contains(Kind.RESTART);
    assertThat(listElement)
        .map(PhysicalTenantKeyClassification.Classification::kind)
        .contains(Kind.REJECT);
  }

  @Test
  void shouldAcceptAnyPropertyWhenATenantIsCreated() {
    // when
    final var violations =
        classification.validate(
            PhysicalTenantConfigChange.create(
                "tenant", with("data.secondary-storage.type", "none")));

    // then
    assertThat(violations).isEmpty();
  }

  private static Map<String, String> with(final String name, final String value) {
    final var proposed = new HashMap<>(CURRENT);
    proposed.put(name, value);
    return proposed;
  }

  private static PhysicalTenantConfigChange update(final Map<String, String> proposed) {
    return new PhysicalTenantConfigChange("tenant", Optional.of(CURRENT), proposed);
  }
}
