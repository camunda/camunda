/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import io.camunda.zeebe.util.VersionUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

/**
 * Provides combinations of versions that should be compatible with each other. This matrix is used
 * in {@link RollingUpdateTest}.
 *
 * <p>Currently we only test between the previous minor version and the current development version.
 */
public class VersionCompatibilityMatrix implements ArgumentsProvider {
  private static final String PREVIOUS_VERSION = VersionUtil.getPreviousVersion();

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
    return Stream.of(Arguments.of(PREVIOUS_VERSION, "CURRENT"));
  }
}
