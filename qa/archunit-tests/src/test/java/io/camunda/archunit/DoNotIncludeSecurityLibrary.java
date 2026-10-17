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
 * Excludes classes loaded from the {@code camunda-security-library} artifacts.
 *
 * <p>CSL shares the {@code io.camunda} package root with this repository, so a rule scoped by
 * package alone also governs library code. That is the wrong scope for a rule this repository has
 * to keep green: a CSL release that legitimately reshapes its own beans would fail the build with
 * no cause to fix here, and would block the next version bump. Use this alongside {@link
 * DoNotIncludeTestsOrTestJars} for rules that should only judge code we can actually change.
 */
public final class DoNotIncludeSecurityLibrary implements ImportOption {

  @Override
  public boolean includes(final Location location) {
    return !location.contains("camunda-security-library");
  }
}
