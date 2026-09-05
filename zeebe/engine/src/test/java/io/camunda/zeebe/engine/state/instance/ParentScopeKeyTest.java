/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

final class ParentScopeKeyTest {

  @Test
  void shouldReadScopeWrittenWithoutEmptyFlagAsNotEmpty() {
    // given a record as written before the empty flag existed, e.g. from an older snapshot
    final var legacy = new LegacyParentScopeKey();
    legacy.keyProp.setValue(1234L);
    final var buffer = new ExpandableArrayBuffer();
    final int length = legacy.write(buffer, 0);

    // when
    final var scope = new ParentScopeKey();
    scope.wrap(buffer, 0, length);

    // then the scope must be treated as possibly holding variables
    assertThat(scope.get()).isEqualTo(1234L);
    assertThat(scope.isEmpty()).isFalse();
  }

  private static final class LegacyParentScopeKey extends UnpackedObject {
    private final LongProperty keyProp = new LongProperty("parentScopeKey", -1L);

    LegacyParentScopeKey() {
      super(1);
      declareProperty(keyProp);
    }
  }
}
