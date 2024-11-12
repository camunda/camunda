/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metadata;

import static io.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static io.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public final class PreviousVersion {
  public static final String RAW_PREVIOUS_VERSION = "${project.previousVersion}";
  public static final String PREVIOUS_VERSION = stripToPlainVersion(RAW_PREVIOUS_VERSION);
  public static final String PREVIOUS_VERSION_MAJOR_MINOR = getMajorAndMinor(PREVIOUS_VERSION);
}
