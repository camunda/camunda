/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the lake translator's working state, held in one RocksDB under the state
 * directory: the open process instances, the open (non-root) element instances, the root-scope
 * variable entries, and the variant-k1 accumulators/name map. There is no offset family — the
 * lake's snapshot summary is the offset authority (see {@link
 * io.camunda.analytics.lake.state.TranslatorState}).
 */
public enum LakeColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default column family). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Open process instances: {@code processInstanceKey -> OpenInstance}. */
  OPEN_INSTANCES(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Open non-root element instances: {@code elementInstanceKey -> OpenElement}. */
  OPEN_ELEMENTS(2, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Root-scope variable entries: {@code processInstanceKey(8, big-endian) ++ nameUtf8 ->
   * valueJson}. A process instance's variables are read/cleared by a {@code processInstanceKey}
   * prefix scan (Zeebe-style per-entry storage, not a map blob).
   */
  VARIABLES(3, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Open instances' variant-k1 accumulators: {@code processInstanceKey -> VariantAccumulator}. See
   * {@code io.camunda.analytics.lake.translate.LakeTranslator}'s "Variant capture" javadoc section.
   */
  VARIANT_ACCUMULATORS(4, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The variant-k1 per-process element/flow name map: {@code bpmnProcessId(utf8) ++ h32(4,
   * big-endian) -> VariantName}. The fixed-width {@code h32} suffix makes the key unambiguous
   * without a length prefix (see {@code RocksDbTranslatorState#variantNameKey}'s own javadoc).
   */
  VARIANT_NAMES(5, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  LakeColumnFamilies(final int value, final ColumnFamilyScope scope) {
    this.value = value;
    this.scope = scope;
  }

  @Override
  public int getValue() {
    return value;
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return scope;
  }
}
