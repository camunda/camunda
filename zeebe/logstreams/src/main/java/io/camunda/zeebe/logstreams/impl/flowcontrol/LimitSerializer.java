/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class LimitSerializer {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.addMixIn(StabilizingAIMDLimit.class, AIMDLimitMixIn.class);
    OBJECT_MAPPER.addMixIn(WindowedLimit.class, WindowedLimitMixIn.class);
    OBJECT_MAPPER.addMixIn(VegasLimit.class, VegasLimitMixIn.class);
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  public static byte[] serialize(final FlowControlLimits flowControlStatus) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(flowControlStatus);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static JsonNode deserialize(final byte[] bytes) {
    try {
      return OBJECT_MAPPER.readTree(bytes);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Mixin to support serialization of {@link WindowedLimit} instances. */
  @JsonIncludeProperties({"delegate"})
  @JsonAutoDetect(fieldVisibility = Visibility.ANY)
  private static final class WindowedLimitMixIn {}

  /** Mixin to support serialization of {@link VegasLimit} instances. */
  @JsonIncludeProperties({"limit", "estimatedLimit", "rtt_noload", "maxLimit", "smoothing"})
  @JsonAutoDetect(fieldVisibility = Visibility.ANY)
  private static final class VegasLimitMixIn {}

  /** Mixin to support serialization of {@link StabilizingAIMDLimit} instances. */
  @JsonIncludeProperties({"limit", "minLimit", "maxLimit", "backoffRatio", "expectedRTT"})
  @JsonAutoDetect(fieldVisibility = Visibility.ANY)
  private static final class AIMDLimitMixIn {}
}
