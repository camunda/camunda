/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Guarantees that this MemberId is from a zeebe broker, not any other applications. */
public class BrokerId extends MemberId {

  BrokerId(final @Nullable String zone, final int nodeIdx, final String id) {
    super(zone, nodeIdx, Objects.requireNonNull(id));
    if (!buildMemberIdString(zone, nodeIdx).equals(id)) {
      throw new IllegalArgumentException(
          "Expected id to be consistent with zone & nodeIdx, but they are: zone=%s, nodeIdx=%d, id=%s"
              .formatted(zone, nodeIdx, id));
    }
  }
}
