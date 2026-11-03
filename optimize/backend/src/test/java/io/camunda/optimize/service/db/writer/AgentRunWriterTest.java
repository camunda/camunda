/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunDerivedFields;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunRecordFolder.FoldResult;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The painless merge script recomputes {@code limitHit} for runs spanning several import pages.
 * Painless can't run in a unit test, so these tests pin the script to the Java rules: it takes the
 * limit names from {@link AgentRunDerivedFields#LIMITS} and checks the same counter against the
 * same limit for each of them.
 */
class AgentRunWriterTest {

  // counter and limit field the script compares for AgentRunDerivedFields.LIMITS.get(i)
  private static final List<List<String>> SCRIPT_CHECKS =
      List.of(
          List.of("modelCalls", "maxModelCalls"),
          List.of("toolCalls", "maxToolCalls"),
          List.of("totalTokens", "maxTokens"));

  private final AgentRunWriter underTest =
      new AgentRunWriter(new ObjectMapper().registerModule(new JavaTimeModule()));

  @Test
  void shouldPassTheJavaLimitNamesToTheMergeScript() {
    // when
    final ScriptData script = scriptFor(new AgentRunDto());

    // then
    assertThat(script.params().get("limits")).isEqualTo(AgentRunDerivedFields.LIMITS);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void shouldNameAReachedLimitTheSameWayInJavaAndInTheMergeScript(final int limit) {
    // given a run that reached only this limit
    final AgentRunDto run = new AgentRunDto();
    run.setModelCalls(5L);
    run.setToolCalls(5L);
    run.setInputTokens(5L);
    run.setMaxModelCalls(100L);
    run.setMaxToolCalls(100L);
    run.setMaxTokens(100L);
    final List<Consumer<AgentRunDto>> reach =
        List.of(r -> r.setMaxModelCalls(5L), r -> r.setMaxToolCalls(5L), r -> r.setMaxTokens(5L));
    reach.get(limit).accept(run);

    // when
    AgentRunDerivedFields.apply(run);
    final String script = scriptFor(run).scriptString();

    // then
    final String counter = SCRIPT_CHECKS.get(limit).get(0);
    final String max = SCRIPT_CHECKS.get(limit).get(1);
    assertThat(run.getLimitHit()).containsExactly(AgentRunDerivedFields.LIMITS.get(limit));
    assertThat(script)
        .contains(
            "if (reached(doc.%s, doc.%s)) { hit.add(params.limits[%d]); }"
                .formatted(counter, max, limit));
  }

  private ScriptData scriptFor(final AgentRunDto run) {
    run.setAgentInstanceId("1");
    run.setLastRecordPosition(1L);
    return underTest
        .generateAgentRunImports(List.of(new FoldResult(run, List.of())))
        .getFirst()
        .getScriptData();
  }
}
