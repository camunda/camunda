/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.check;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Incompatible.UseOfPreReleaseVersion;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class VersionCompatibilityCheckTest {

  @Nested
  final class IncompatibleResults {

    @Test
    public void shouldRejectUpgradeToPreRelease() {
      assertThat(check("8.0.0", "8.0.0-alpha1")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.1", "8.1.0-alpha1")).isInstanceOf(UseOfPreReleaseVersion.class);
    }

    @Test
    public void shouldRejectChangeOfPreRelease() {
      assertThat(check("8.0.0-alpha1", "8.0.0-alpha2")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-alpha1", "8.0.0-beta1")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-beta1", "8.0.0-rc1")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-rc1", "8.0.0")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-alpha1", "8.1.0")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-alpha2", "8.0.1")).isInstanceOf(UseOfPreReleaseVersion.class);
    }

    @Test
    public void shouldRejectUpgradeFromPreRelease() {
      assertThat(check("8.0.0-alpha1", "8.0.0")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-alpha1", "8.0.1")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-beta1", "8.0.1")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.0.0-rc1", "8.1.0")).isInstanceOf(UseOfPreReleaseVersion.class);
      assertThat(check("8.5.3", "8.5.3-alpha1"))
          .isInstanceOf(Incompatible.UseOfPreReleaseVersion.class);
      assertThat(check("8.1.0", "8.1.0-alpha1"))
          .isInstanceOf(Incompatible.UseOfPreReleaseVersion.class);
    }

    @Test
    public void shouldRejectDowngrades() {
      assertThat(check("9.0.0", "8.0.0")).isInstanceOf(Incompatible.MajorDowngrade.class);
      assertThat(check("9.0.0", "8.1.0")).isInstanceOf(Incompatible.MajorDowngrade.class);
      assertThat(check("8.5.0", "8.4.0")).isInstanceOf(Incompatible.MinorDowngrade.class);
      assertThat(check("8.5.0", "8.4.1")).isInstanceOf(Incompatible.MinorDowngrade.class);
      assertThat(check("8.5.3", "8.5.2")).isInstanceOf(Incompatible.PatchDowngrade.class);
      assertThat(check("8.5.3", "8.5.0")).isInstanceOf(Incompatible.PatchDowngrade.class);
    }

    @Test
    public void shouldRejectMajorUpgrades() {
      assertThat(check("8.0.0", "9.0.0")).isInstanceOf(Incompatible.MajorUpgrade.class);
      assertThat(check("8.1.0", "9.0.0")).isInstanceOf(Incompatible.MajorUpgrade.class);
    }

    @Test
    public void shouldRejectSkippedMinorVersions() {
      assertThat(check("8.0.0", "8.2.0")).isInstanceOf(Incompatible.SkippedMinorVersion.class);
      assertThat(check("8.0.2", "8.3.0")).isInstanceOf(Incompatible.SkippedMinorVersion.class);
    }
  }

  @Nested
  final class IndeterminateResults {
    @Test
    void shouldDetectUnknownVersions() {
      assertThat(check(null, "8.0.0")).isInstanceOf(Indeterminate.PreviousVersionUnknown.class);
      assertThat(check("8.0.0", null)).isInstanceOf(Indeterminate.CurrentVersionUnknown.class);
    }

    @Test
    void shouldDetectInvalidVersions() {
      assertThat(check("dev", "1.2.3")).isInstanceOf(Indeterminate.PreviousVersionInvalid.class);
      assertThat(check("1.2.3", "dev")).isInstanceOf(Indeterminate.CurrentVersionInvalid.class);
    }
  }

  @Nested
  final class CompatibleResults {

    @Test
    void shouldAcceptPatchUpgrades() {
      assertThat(check("8.0.0", "8.0.1")).isInstanceOf(Compatible.PatchUpgrade.class);
      assertThat(check("8.1.4", "8.1.5")).isInstanceOf(Compatible.PatchUpgrade.class);
      assertThat(check("8.1.4", "8.1.8")).isInstanceOf(Compatible.PatchUpgrade.class);
    }

    @Test
    void shouldAcceptMinorUpgrades() {
      assertThat(check("8.0.0", "8.1.0")).isInstanceOf(Compatible.MinorUpgrade.class);
      assertThat(check("8.1.3", "8.2.0")).isInstanceOf(Compatible.MinorUpgrade.class);
      assertThat(check("8.1.3", "8.2.5")).isInstanceOf(Compatible.MinorUpgrade.class);
    }

    @Test
    void shouldAcceptSameVersions() {
      assertThat(check("8.0.0", "8.0.0")).isInstanceOf(Compatible.SameVersion.class);
      assertThat(check("8.0.0-alpha1", "8.0.0-alpha1")).isInstanceOf(Compatible.SameVersion.class);
      assertThat(check("8.1.0-alpha1+build123", "8.1.0-alpha1+build234"))
          .isInstanceOf(Compatible.SameVersion.class);
    }
  }
}
