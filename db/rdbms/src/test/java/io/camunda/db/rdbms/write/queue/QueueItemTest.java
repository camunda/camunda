/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.queue.QueueItem.QueueItemBuilder;
import org.junit.jupiter.api.Test;

class QueueItemTest {

  @Test
  void shouldDefaultOrderToZeroWhenUsingFiveArgumentConstructor() {
    // given
    final var contextType = ContextType.PROCESS_INSTANCE;

    // when
    final var item =
        new QueueItem(contextType, WriteStatementType.UPDATE, 1L, "statement", "parameter");

    // then
    assertThat(item.order())
        .as("an item created without an explicit order falls back to the default order")
        .isZero();
  }

  @Test
  void shouldPreserveOrderWhenCopyingQueueItem() {
    // given
    final var original =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.UPDATE,
            1L,
            "statement",
            "original",
            42);

    // when
    final var copy = original.copy(b -> b.parameter("changed"));

    // then
    assertThat(copy.order())
        .as("copying an item keeps its explicit order, so merged items keep their position")
        .isEqualTo(42);
    assertThat(copy.parameter())
        .as("the copy carries the parameter set through the builder function")
        .isEqualTo("changed");
    assertThat(original.parameter())
        .as("the original item is not modified by copying it")
        .isEqualTo("original");
  }

  @Test
  void shouldSetOrderThroughBuilder() {
    // given
    final var builder =
        new QueueItemBuilder()
            .contextType(ContextType.PROCESS_INSTANCE)
            .statementType(WriteStatementType.UPDATE)
            .id(1L)
            .statementId("statement")
            .parameter("parameter");

    // when
    final var defaultOrderItem = builder.build();
    final var explicitOrderItem = builder.order(7).build();

    // then
    assertThat(defaultOrderItem.order())
        .as("a builder without an explicit order builds an item with the default order")
        .isZero();
    assertThat(explicitOrderItem.order())
        .as("a builder with an explicit order builds an item with that order")
        .isEqualTo(7);
  }
}
