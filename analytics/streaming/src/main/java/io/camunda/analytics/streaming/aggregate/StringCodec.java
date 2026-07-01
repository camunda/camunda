/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.nio.charset.StandardCharsets;

/**
 * Byte codec for a {@code String} grouping key (UTF-8). Reusable for any rollup keyed by a single
 * string dimension, such as a tenant id.
 */
public final class StringCodec implements Codec<String> {

  @Override
  public byte[] encode(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String decode(final byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
