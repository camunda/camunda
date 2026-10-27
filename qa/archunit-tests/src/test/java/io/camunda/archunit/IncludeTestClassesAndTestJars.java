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
 * Includes classes from the standard test source directories ({@code target/test-classes}, {@code
 * build/classes/test}, {@code out/test}) and from Maven test-jars ({@code *-tests.jar}).
 *
 * <p>Use this option for ArchUnit rules that scan test code from other modules pulled in as
 * test-jar dependencies. {@link ImportOption.OnlyIncludeTests} alone does not match the location
 * URIs of classes loaded from a Maven test-jar.
 *
 * <p>The companion {@link DoNotIncludeTestsOrTestJars} should be used by rules in this module that
 * scan production code in packages overlapping with such test-jars (e.g. {@code
 * io.camunda.zeebe.gateway.rest..}); plain {@link ImportOption.DoNotIncludeTests} treats test-jar
 * entries as production code and would let test classes leak into the analysis.
 */
public final class IncludeTestClassesAndTestJars implements ImportOption {

  private static final ImportOption ONLY_INCLUDE_TESTS = new ImportOption.OnlyIncludeTests();

  @Override
  public boolean includes(final Location location) {
    return ONLY_INCLUDE_TESTS.includes(location) || location.contains("-tests.jar");
  }
}
