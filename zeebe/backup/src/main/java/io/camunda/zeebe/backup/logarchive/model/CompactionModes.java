/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import org.jspecify.annotations.Nullable;

/**
 * The compaction mode of a timeline (spec section 25.1). The JSON files omit {@code CLUSTER}, the
 * mode of every timeline written before modes existed, so that their bytes stay unchanged; a
 * missing field reads as {@code CLUSTER}.
 */
public final class CompactionModes {

  /** Mode A: the cluster merges and releases, the event plane compacts below the released index. */
  public static final String CLUSTER = "CLUSTER";

  /** Mode B: one authority compacts and deletes, the cluster declares a restore hold. */
  public static final String AUTHORITY = "AUTHORITY";

  private CompactionModes() {}

  /** The value stored in the JSON model: null for {@code CLUSTER}, which is omitted. */
  public static @Nullable String normalize(final @Nullable String mode) {
    return mode == null || mode.isEmpty() || CLUSTER.equals(mode) ? null : mode;
  }

  public static boolean isAuthority(final @Nullable String mode) {
    return AUTHORITY.equals(mode);
  }
}
