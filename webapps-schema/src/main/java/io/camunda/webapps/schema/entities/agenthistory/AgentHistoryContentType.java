/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agenthistory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity-layer enum for the content type of a history entry content block. Only the three concrete
 * types ({@code TEXT}, {@code DOCUMENT}, {@code OBJECT}) are represented — there is no {@code
 * UNKNOWN} fallback because unsupported protocol values (e.g. {@code UNSPECIFIED}) are filtered out
 * via {@link #isSupported} before any mapping takes place.
 */
public enum AgentHistoryContentType {
  TEXT,
  DOCUMENT,
  OBJECT;

  private static final Set<String> SUPPORTED_NAMES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  /** Returns whether the given content type name is supported by this enum. */
  public static boolean isSupported(final String contentType) {
    return SUPPORTED_NAMES.contains(contentType);
  }
}
