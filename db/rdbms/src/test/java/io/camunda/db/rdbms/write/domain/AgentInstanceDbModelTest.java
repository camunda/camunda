/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceToolDbValue;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AgentInstanceDbModelTest {

  @Test
  void shouldStoreBothFormsWhenBuiltFromStructuredInput() {
    // given
    final var tool = new AgentInstanceToolDbValue("search", "search the web", "el-1");

    // when
    final var model = new AgentInstanceDbModel.Builder().toolValues(List.of(tool)).build();

    // then
    assertThat(model.toolValues()).containsExactly(tool);
    assertThat(model.tools()).contains("\"name\":\"search\"");
  }

  @Test
  void shouldLazilyDeserializeToolsWhenOnlyJsonIsSet() {
    // given — simulate a model hydrated from the DB: only the JSON form is populated
    final var model =
        new AgentInstanceDbModel.Builder(
                "[{\"name\":\"search\",\"description\":\"search the web\",\"elementId\":\"el-1\"}]")
            .build();

    // when
    final List<AgentInstanceToolDbValue> deserialized = model.toolValues();

    // then
    assertThat(deserialized)
        .containsExactly(new AgentInstanceToolDbValue("search", "search the web", "el-1"));
  }

  @Test
  void shouldDeriveToolValuesFreshOnEveryCall() {
    // given
    final var model =
        new AgentInstanceDbModel.Builder()
            .toolValues(List.of(new AgentInstanceToolDbValue("search", null, null)))
            .build();

    // when — call twice
    final var first = model.toolValues();
    final var second = model.toolValues();

    // then — equal content, but freshly deserialized each time (no cache)
    assertThat(first).isEqualTo(second).isNotSameAs(second);
  }

  @Test
  void shouldReturnNullWhenBothFormsAreAbsent() {
    final var model = new AgentInstanceDbModel.Builder().build();
    assertThat(model.toolValues()).isNull();
  }

  @Test
  void shouldReturnNullWhenJsonIsEmpty() {
    // Oracle treats empty CLOB as NULL; MyBatis maps it back to "" on read.
    final var model = new AgentInstanceDbModel.Builder("").build();
    assertThat(model.toolValues()).isNull();
  }

  @Test
  void shouldPreserveRawToolsJsonByteForByteThroughQueueMerge() {
    // given — a DB-hydrated instance whose stored JSON is non-canonically formatted (spaced out,
    // key order Jackson would never itself produce for this record)
    final var nonCanonicalJson =
        "[ { \"description\": \"search the web\", \"name\": \"search\", \"elementId\": \"el-1\" } ]";
    final var original =
        new AgentInstanceDbModel.Builder(nonCanonicalJson).agentInstanceKey(1L).build();
    final var queueItem =
        new QueueItem(
            ContextType.AGENT_INSTANCE, WriteStatementType.INSERT, 1L, "statement", original);
    final Function<AgentInstanceDbModel.Builder, AgentInstanceDbModel.Builder> mergeFunction =
        b -> b.status(AgentInstanceStatus.IDLE);
    final var merger =
        new UpsertMerger<>(
            ContextType.AGENT_INSTANCE, 1L, AgentInstanceDbModel.class, mergeFunction);

    // when — coalesce a change to an unrelated field, never touching toolValues()
    final var merged = (AgentInstanceDbModel) merger.merge(queueItem).parameter();

    // then — the raw JSON is carried through completely unparsed, not reserialized
    assertThat(merged.tools()).isEqualTo(nonCanonicalJson);
    assertThat(merged.status()).isEqualTo(AgentInstanceStatus.IDLE);
  }
}
