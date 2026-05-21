/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.util;

public final class ResourceUtils {

  private ResourceUtils() {}

  /**
   * Derives the resource type from the resource name by extracting the file extension. For example,
   * {@code "script.rpa"} returns {@code "rpa"}, and {@code "process.bpmn"} returns {@code "bpmn"}.
   *
   * @param resourceName the resource file name
   * @return the lowercase file extension, or {@code null} if none can be derived
   */
  public static String deriveResourceType(final String resourceName) {
    if (resourceName == null || resourceName.isEmpty()) {
      return null;
    }
    final int lastDotIndex = resourceName.lastIndexOf('.');
    if (lastDotIndex > 0 && lastDotIndex < resourceName.length() - 1) {
      return resourceName.substring(lastDotIndex + 1).toLowerCase();
    }
    return null;
  }
}
