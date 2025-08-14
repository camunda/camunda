/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.exporter.http.matcher.Filter;
import io.camunda.exporter.http.matcher.FilterRecordMatcher;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RuleMatcherTest {

  final FilterRecordMatcher incidentValueTypeMatcher =
      new FilterRecordMatcher(List.of(new Filter(ValueType.INCIDENT, Set.of())));
  private final FilterRecordMatcher incidentIntentCreatedMatcher =
      new FilterRecordMatcher(
          List.of(new Filter(ValueType.INCIDENT, Set.of(IncidentIntent.CREATED.name()))));
  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));

  @Test
  void testValueTypeMatcher() {
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
  }
}
