/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

public class AwaitProcessInstanceResultMetadataTest {

  @Test
  public void fetchVariablesShouldAlsoWorkWhenItsOwnPropertyIsPassedIn() {
    // given
    final var sut = new AwaitProcessInstanceResultMetadata();
    final var fetchVariables = sut.fetchVariables();
    final var stringBuffer = BufferUtil.wrapString("test");
    fetchVariables.add().wrap(stringBuffer);

    // when
    sut.setFetchVariables(fetchVariables);

    // then
    final List<DirectBuffer> actualList = new ArrayList<>();
    sut.fetchVariables().iterator().forEachRemaining(item -> actualList.add(item.getValue()));

    assertThat(actualList).hasSize(1).containsExactly(stringBuffer);
  }
}
