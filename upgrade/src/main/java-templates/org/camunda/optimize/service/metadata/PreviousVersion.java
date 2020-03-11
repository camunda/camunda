/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public final class PreviousVersion {
  public static final String RAW_PREVIOUS_VERSION = "${project.previousVersion}";
  public static final String PREVIOUS_VERSION = stripToPlainVersion(RAW_PREVIOUS_VERSION);
}