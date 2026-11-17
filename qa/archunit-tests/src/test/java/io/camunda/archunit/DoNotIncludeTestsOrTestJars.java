/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Excludes classes from the standard test source directories AND from Maven test-jars ({@code
 * *-tests.jar}).
 *
 * <p>Companion to {@link IncludeTestClassesAndTestJars}. Use this for production-code rules in
 * modules whose {@code pom.xml} pulls in {@code <type>test-jar</type>} dependencies: plain {@link
 * ImportOption.DoNotIncludeTests} only filters by directory path, so classes loaded from a {@code
 * *-tests.jar} would otherwise be treated as production code.
 */
public final class DoNotIncludeTestsOrTestJars implements ImportOption {

  private static final ImportOption DO_NOT_INCLUDE_TESTS = new ImportOption.DoNotIncludeTests();

  @Override
  public boolean includes(final Location location) {
    return DO_NOT_INCLUDE_TESTS.includes(location) && !location.contains("-tests.jar");
  }
}
