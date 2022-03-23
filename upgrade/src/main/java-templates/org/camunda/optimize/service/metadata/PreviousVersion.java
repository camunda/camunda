/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;
import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;

public final class PreviousVersion {
  public static final String RAW_PREVIOUS_VERSION = "${project.previousVersion}";
  public static final String PREVIOUS_VERSION = stripToPlainVersion(RAW_PREVIOUS_VERSION);
  public static final String PREVIOUS_VERSION_MAJOR_MINOR = getMajorAndMinor(PREVIOUS_VERSION);
}
