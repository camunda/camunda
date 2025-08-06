/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.http.matcher.RuleRecordMatcher;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
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

  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));

  @Test
  void testRuleMatcher() throws Throwable {
    final var objectMapper = new ObjectMapper().registerModule(new ZeebeProtocolModule());

    final var incidentResolvedRecord =
        objectMapper.writeValueAsString(
            factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.RESOLVED));

    final var incidentCreatedRecord =
        objectMapper.writeValueAsString(
            factory.generateRecordWithIntent(ValueType.INCIDENT, IncidentIntent.CREATED));

    final var jobRecord = objectMapper.writeValueAsString(factory.generateRecord(ValueType.JOB));

    final var incidentMatcher = new RuleRecordMatcher(List.of(incidentMatchingRule));
    assertTrue(incidentMatcher.matches(incidentResolvedRecord));
    assertTrue(incidentMatcher.matches(incidentCreatedRecord));
    assertFalse(incidentMatcher.matches(jobRecord));

    final var incidentCreatedMatcher = new RuleRecordMatcher(List.of(incidentCreatedMatchingRule));
    assertTrue(incidentCreatedMatcher.matches(incidentCreatedRecord));
    assertFalse(incidentCreatedMatcher.matches(incidentResolvedRecord));
    assertFalse(incidentCreatedMatcher.matches(jobRecord));

    final var jobMatcher = new RuleRecordMatcher(List.of(jobMatchingRule));
    assertFalse(jobMatcher.matches(incidentCreatedRecord));
    assertFalse(jobMatcher.matches(incidentResolvedRecord));
    assertTrue(jobMatcher.matches(jobRecord));
  }
}
