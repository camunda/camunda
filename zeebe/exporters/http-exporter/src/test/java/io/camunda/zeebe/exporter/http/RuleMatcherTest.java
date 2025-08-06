/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.http.matcher.CombinedMatcher;
import io.camunda.zeebe.exporter.http.matcher.Filter;
import io.camunda.zeebe.exporter.http.matcher.FilterRecordMatcher;
import io.camunda.zeebe.exporter.http.matcher.RuleRecordMatcher;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RuleMatcherTest {

  String incidentMatchingRule =
      """
    {"valueType": ["INCIDENT"]}"
  """;
  String jobMatchingRule =
      """
    {"valueType": ["JOB"]}"
  """;

  String incidentCreatedMatchingRule =
      """
      {
        "valueType": ["INCIDENT"],
        "intent": ["CREATED"]
      }

""";
  final RuleRecordMatcher incidentMatcher = new RuleRecordMatcher(List.of(incidentMatchingRule));
  final RuleRecordMatcher jobMatcher = new RuleRecordMatcher(List.of(jobMatchingRule));
  final FilterRecordMatcher incidentValueTypeMatcher =
      new FilterRecordMatcher(List.of(new Filter(ValueType.INCIDENT, Set.of())));
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());
  private final FilterRecordMatcher incidentIntentCreatedMatcher =
      new FilterRecordMatcher(
          List.of(new Filter(ValueType.INCIDENT, Set.of(IncidentIntent.CREATED.name()))));
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));
  private final RuleRecordMatcher incidentCreatedMatcher =
      new RuleRecordMatcher(List.of(incidentCreatedMatchingRule));

  final CombinedMatcher combinedMatcher =
      new CombinedMatcher(incidentIntentCreatedMatcher, incidentCreatedMatcher);

  @Test
  void testRuleMatcher() throws Throwable {
    final var incidentResolvedRecord =
        objectMapper.writeValueAsString(
            factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.RESOLVED));

    final var incidentCreatedRecord =
        objectMapper.writeValueAsString(
            factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.CREATED));

    final var jobRecord = objectMapper.writeValueAsString(factory.generateRecord(ValueType.JOB));

    assertThat(incidentMatcher.matches(incidentResolvedRecord)).isTrue();
    assertThat(incidentMatcher.matches(incidentCreatedRecord)).isTrue();
    assertThat(incidentMatcher.matches(jobRecord)).isFalse();

    assertThat(incidentCreatedMatcher.matches(incidentCreatedRecord)).isTrue();
    assertThat(incidentCreatedMatcher.matches(incidentResolvedRecord)).isFalse();
    assertThat(incidentCreatedMatcher.matches(jobRecord)).isFalse();

    assertThat(jobMatcher.matches(incidentCreatedRecord)).isFalse();
    assertThat(jobMatcher.matches(incidentResolvedRecord)).isFalse();
    assertThat(jobMatcher.matches(jobRecord)).isTrue();
  }

  @Test
  void testValueTypeMatcher() throws Throwable {
    final var jobRecord = factory.generateRecord(ValueType.JOB);

    final var incidentResolvedRecord =
        factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.RESOLVED);

    final var incidentCreatedRecord =
        factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.CREATED);

    assertThat(incidentValueTypeMatcher.matches(incidentResolvedRecord)).isTrue();
    assertThat(incidentValueTypeMatcher.matches(incidentCreatedRecord)).isTrue();
    assertThat(incidentValueTypeMatcher.matches(jobRecord)).isFalse();

    assertThat(incidentIntentCreatedMatcher.matches(incidentCreatedRecord)).isTrue();
    assertThat(incidentIntentCreatedMatcher.matches(incidentResolvedRecord)).isFalse();
    assertThat(incidentIntentCreatedMatcher.matches(jobRecord)).isFalse();

    assertThat(
            combinedMatcher.matches(
                incidentCreatedRecord, objectMapper.writeValueAsString(incidentCreatedRecord)))
        .isTrue();
    assertThat(
            combinedMatcher.matches(
                incidentResolvedRecord, objectMapper.writeValueAsString(incidentResolvedRecord)))
        .isFalse();
    assertThat(combinedMatcher.matches(jobRecord, objectMapper.writeValueAsString(jobRecord)))
        .isFalse();
  }
}
