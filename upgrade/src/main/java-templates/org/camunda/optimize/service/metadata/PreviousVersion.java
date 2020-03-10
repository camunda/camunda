/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class PreviousVersion {
  public static final String RAW_PREVIOUS_VERSION = "${project.previousVersion}";
  public static final String PREVIOUS_VERSION = stripToPlainVersion(RAW_PREVIOUS_VERSION);

  public static final String stripToPlainVersion(final String rawVersion) {
    // extract plain <major>.<minor>.<patch> version, strip everything else
    return Arrays.stream(rawVersion.split("[^0-9]"))
      .limit(3)
      .filter(part -> part.chars().allMatch(Character::isDigit))
      .collect(Collectors.joining("."));
  }
}