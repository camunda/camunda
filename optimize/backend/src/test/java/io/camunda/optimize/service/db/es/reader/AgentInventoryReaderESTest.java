/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentInventoryReaderESTest {

  private static final Instant LAST_END = Instant.parse("2026-10-20T10:00:00Z");

  @Test
  void shouldRateIncidentRunsAndTakeTheLatestEndDateAsLastRun() {
    // given 8 runs of an agent without definition, 2 of them failed
    final StringTermsBucket runs = runs(8, 2, "EXTERNAL_AGENT", List.of("claude", "gpt-4o"));

    // when
    final AgentInventoryEntry entry = AgentInventoryReaderES.toEntry("p:agent", null, runs);

    // then
    assertThat(entry.runs()).isEqualTo(8);
    assertThat(entry.incidentRate()).isEqualTo(25.0);
    assertThat(entry.models()).containsExactly("claude", "gpt-4o");
    assertThat(entry.agentType()).isEqualTo("EXTERNAL_AGENT");
    assertThat(entry.processDefinitionKey()).isEqualTo("p");
    assertThat(entry.lastRunDate()).isEqualTo(OffsetDateTime.ofInstant(LAST_END, ZoneOffset.UTC));
    assertThat(entry.totalTokens()).isEqualTo(800.0);
    assertThat(entry.avgTokens()).isEqualTo(100.0);
    assertThat(entry.durationP50Ms()).isEqualTo(1000.0);
    assertThat(entry.durationP95Ms()).isEqualTo(3000.0);
  }

  @Test
  void shouldPreferTheDefinitionForNameTypeAndProcess() {
    // given
    final AgentDefinitionDto definition = new AgentDefinitionDto();
    definition.setName("Review agent");
    definition.setAgentType("AI_AGENT_SUB_PROCESS");
    definition.setProcessDefinitionKey("loan");

    // when
    final AgentInventoryEntry entry =
        AgentInventoryReaderES.toEntry("loan:review", definition, null);

    // then the agent is listed without runs
    assertThat(entry.name()).isEqualTo("Review agent");
    assertThat(entry.agentType()).isEqualTo("AI_AGENT_SUB_PROCESS");
    assertThat(entry.processDefinitionKey()).isEqualTo("loan");
    assertThat(entry.runs()).isZero();
    assertThat(entry.incidentRate()).isNull();
    assertThat(entry.lastRunDate()).isNull();
  }

  private static StringTermsBucket runs(
      final long runs, final long failed, final String agentType, final List<String> models) {
    return StringTermsBucket.of(
        b ->
            b.key(FieldValue.of("p:agent"))
                .docCount(runs)
                .aggregations(
                    Map.of(
                        AgentInventoryReaderES.INCIDENTS,
                        Aggregate.of(a -> a.filter(f -> f.docCount(failed))),
                        "duration",
                        Aggregate.of(
                            a ->
                                a.tdigestPercentiles(
                                    t ->
                                        t.values(
                                            v ->
                                                v.keyed(
                                                    Map.of("50.0", "1000.0", "95.0", "3000.0"))))),
                        "tokens",
                        Aggregate.of(a -> a.avg(v -> v.value(100.0))),
                        "tokensTotal",
                        Aggregate.of(a -> a.sum(v -> v.value(800.0))),
                        "lastRun",
                        Aggregate.of(a -> a.max(v -> v.value((double) LAST_END.toEpochMilli()))),
                        "models",
                        terms(models),
                        "agentType",
                        terms(List.of(agentType)),
                        "process",
                        terms(List.of("p")))));
  }

  private static Aggregate terms(final List<String> keys) {
    return Aggregate.of(
        a ->
            a.sterms(
                t ->
                    t.sumOtherDocCount(0L)
                        .buckets(
                            b ->
                                b.array(
                                    keys.stream()
                                        .map(
                                            key ->
                                                StringTermsBucket.of(
                                                    k -> k.key(FieldValue.of(key)).docCount(1)))
                                        .toList()))));
  }
}
