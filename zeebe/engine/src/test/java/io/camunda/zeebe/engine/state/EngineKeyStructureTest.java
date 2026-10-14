/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.KeyStructure;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.clustervariable.DbClusterVariableScopeKey;
import io.camunda.zeebe.engine.state.jobmetrics.MetricsKey;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies for the keys that implement {@code DbKey} directly in the engine that the declared
 * structure matches the bytes {@code write()} produces.
 */
final class EngineKeyStructureTest {

  @Test
  void shouldDescribeMetricsKeyAsThreeIntegersMatchingItsSerializedSize() {
    // given
    final var key = new MetricsKey(1, 2, 3);
    final var declared = new RecordingStructure();

    // when
    key.describe(declared);
    final var written = key.write(new ExpandableArrayBuffer(), 0);

    // then
    assertThat(declared.calls)
        .containsExactly("int32:jobTypeIndex", "int32:tenantIdIndex", "int32:workerNameIndex");
    assertThat(written).isEqualTo(3 * Integer.BYTES).isEqualTo(MetricsKey.TOTAL_SIZE_BYTES);
  }

  @Test
  void shouldDescribeClusterVariableScopeKeyWithOmittableStringAndOmitItForGlobalScope() {
    // given
    final var scope = new DbEnumValue<>(ClusterVariableScope.class);
    final var scopeKey = new DbString();
    final var key = new DbClusterVariableScopeKey(scope, scopeKey);
    final var declared = new RecordingStructure();
    scopeKey.wrapString("tenant-for-cluster-variable");

    // when
    key.describe(declared);
    scope.setValue(ClusterVariableScope.TENANT);
    final var writtenForTenant = key.write(new ExpandableArrayBuffer(), 0);
    scope.setValue(ClusterVariableScope.GLOBAL);
    final var writtenForGlobal = key.write(new ExpandableArrayBuffer(), 0);

    // then
    assertThat(declared.calls).containsExactly("enumOrdinal:null", "omittableString:scopeKey");
    assertThat(writtenForTenant).isEqualTo(1 + Integer.BYTES + 27);
    assertThat(writtenForGlobal).as("the string is not serialized at all").isEqualTo(1);
  }

  private static final class RecordingStructure implements KeyStructure {
    private final List<String> calls = new ArrayList<>();

    @Override
    public void int64(final String name, final boolean nonNegative) {
      calls.add("int64:" + name);
    }

    @Override
    public void int32(final String name, final boolean nonNegative) {
      calls.add("int32:" + name);
    }

    @Override
    public void int16(final String name, final boolean nonNegative) {
      calls.add("int16:" + name);
    }

    @Override
    public void int8(final String name, final boolean nonNegative) {
      calls.add("int8:" + name);
    }

    @Override
    public void string(final String name) {
      calls.add("string:" + name);
    }

    @Override
    public void omittableString(final String name) {
      calls.add("omittableString:" + name);
    }

    @Override
    public void bytes(final String name) {
      calls.add("bytes:" + name);
    }

    @Override
    public void enumOrdinal(final String name) {
      calls.add("enumOrdinal:" + name);
    }
  }
}
