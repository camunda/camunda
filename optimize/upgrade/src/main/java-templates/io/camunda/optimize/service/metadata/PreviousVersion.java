/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.metadata;

import static io.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static io.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public final class PreviousVersion {
  public static final String RAW_PREVIOUS_VERSION = "${project.previousVersion}";
  public static final String PREVIOUS_VERSION = stripToPlainVersion(RAW_PREVIOUS_VERSION);
  public static final String PREVIOUS_VERSION_MAJOR_MINOR = getMajorAndMinor(PREVIOUS_VERSION);
}
