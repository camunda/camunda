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
import java.util.List;
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
    final var model = new AgentInstanceDbModel();
    model.tools(
        "[{\"name\":\"search\",\"description\":\"search the web\",\"elementId\":\"el-1\"}]");

    // when
    final List<AgentInstanceToolDbValue> deserialized = model.toolValues();

    // then
    assertThat(deserialized)
        .containsExactly(new AgentInstanceToolDbValue("search", "search the web", "el-1"));
  }

  @Test
  void shouldCacheDeserializedToolsOnTheModel() {
    // given
    final var model = new AgentInstanceDbModel();
    model.tools("[{\"name\":\"search\",\"description\":null,\"elementId\":null}]");

    // when — call twice
    final var first = model.toolValues();
    final var second = model.toolValues();

    // then — same instance is returned, so deserialization happened only once
    assertThat(second).isSameAs(first);
  }

  @Test
  void shouldReturnNullWhenBothFormsAreAbsent() {
    final var model = new AgentInstanceDbModel();
    assertThat(model.toolValues()).isNull();
  }

  @Test
  void shouldReturnNullWhenJsonIsEmpty() {
    final var model = new AgentInstanceDbModel();
    model.tools("");
    assertThat(model.toolValues()).isNull();
  }

  @Test
  void shouldInvalidateCachedToolValuesWhenJsonIsReplaced() {
    // given — model with both forms populated (cache primed)
    final var model =
        new AgentInstanceDbModel.Builder()
            .toolValues(List.of(new AgentInstanceToolDbValue("first", null, null)))
            .build();
    assertThat(model.toolValues())
        .containsExactly(new AgentInstanceToolDbValue("first", null, null));

    // when — the JSON form is replaced (simulates the model being re-hydrated from the DB)
    model.tools("[{\"name\":\"second\",\"description\":null,\"elementId\":null}]");

    // then — the cached list is invalidated and the next read re-derives from the new JSON
    assertThat(model.toolValues())
        .containsExactly(new AgentInstanceToolDbValue("second", null, null));
  }
}
