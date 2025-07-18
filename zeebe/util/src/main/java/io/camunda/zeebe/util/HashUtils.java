/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

public class HashUtils {

  public static long getStringHashValue(final String stringValue) {
    return Hashing.murmur3_128().hashString(stringValue, StandardCharsets.UTF_8).asLong();
  }
}
