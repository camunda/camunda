/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RdbmsWritersTest {

  private ExecutionQueue executionQueue;
  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  void setUp() throws Exception {
    // The RdbmsWriters constructor wires ~30 writers and mappers; close() only interacts with the
    // execution queue, so we drive the real close() on a partial mock and stub the two
    // collaborators
    // it touches (flush and the execution queue) instead of building the full object graph.
    executionQueue = mock(ExecutionQueue.class);
    rdbmsWriters = mock(RdbmsWriters.class);
    when(rdbmsWriters.getExecutionQueue()).thenReturn(executionQueue);
    doCallRealMethod().when(rdbmsWriters).close();
  }

  @Test
  void shouldFlushThenResetOnClose() throws Exception {
    // when
    rdbmsWriters.close();

    // then
    final InOrder inOrder = inOrder(rdbmsWriters, executionQueue);
    inOrder.verify(rdbmsWriters).flush(true);
    inOrder.verify(executionQueue).reset();
  }

  @Test
  void shouldResetEvenWhenFlushThrows() {
    // given
    doThrow(new RuntimeException("flush failed")).when(rdbmsWriters).flush(true);

    // when
    assertThatThrownBy(() -> rdbmsWriters.close())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("flush failed");

    // then - the execution queue is reset regardless of the flush failure, so a retried open starts
    // from a clean state
    verify(executionQueue).reset();
  }
}
