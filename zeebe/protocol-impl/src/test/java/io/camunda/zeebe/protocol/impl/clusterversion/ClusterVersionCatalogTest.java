/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.ApplierVersionId;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.GatedCommandId;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class ClusterVersionCatalogTest {

  // -- resolveByVersion -----------------------------------------------------

  @Nested
  class ResolveByVersionTests {

    @Test
    void shouldResolveToMaxWhenTargetEqualsBuildVersion() {
      // given
      final var build = "8.10.5";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("8.10.5", build);

      // then
      assertThat(resolved).contains(Capability.PING_ADMISSION);
    }

    @Test
    void shouldResolveToMaxWhenTargetAboveBuildVersion() {
      // given
      final var build = "8.10.5";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("8.11.0", build);

      // then
      assertThat(resolved).contains(Capability.PING_ADMISSION);
    }

    @Test
    void shouldReturnEmptyWhenTargetBelowBuildVersion() {
      // given — operator asks to land short of what this binary supports
      final var build = "8.10.5";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("8.10.4", build);

      // then — version path refuses; operator must use capability/ordinal instead
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldReturnEmptyForMalformedTarget() {
      // given
      final var build = "8.10.5";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("not-a-version", build);

      // then
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldAdmitAnyValidTargetWhenBuildVersionUnparseable() {
      // given — dev/test build where VersionUtil returns "development"
      final var build = "development";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("8.10.0", build);

      // then — dev binaries accept any well-formed target
      assertThat(resolved).contains(Capability.PING_ADMISSION);
    }

    @Test
    void shouldRefuseMalformedTargetEvenInDevBuild() {
      // given
      final var build = "development";

      // when
      final var resolved = ClusterVersionCatalog.resolveByVersion("nonsense", build);

      // then — target must still be valid semver
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldTreatPreReleaseBuildAsLowerThanItsRelease() {
      // given — running a 8.10.5-SNAPSHOT pre-release binary
      final var build = "8.10.5-SNAPSHOT";

      // when — operator targets the final 8.10.5 release
      final var resolved = ClusterVersionCatalog.resolveByVersion("8.10.5", build);

      // then — 8.10.5 > 8.10.5-SNAPSHOT per semver, so this is allowed
      assertThat(resolved).contains(Capability.PING_ADMISSION);
    }
  }

  // -- resolveByName --------------------------------------------------------

  @Nested
  class ResolveByNameTests {

    @Test
    void shouldResolveKnownCapability() {
      // when
      final var resolved = ClusterVersionCatalog.resolveByName("DEMO_GATED_BRANCH");

      // then
      assertThat(resolved).contains(Capability.DEMO_GATED_BRANCH);
    }

    @Test
    void shouldResolveBaseline() {
      // when
      final var resolved = ClusterVersionCatalog.resolveByName("BASELINE");

      // then
      assertThat(resolved).contains(Capability.BASELINE);
    }

    @Test
    void shouldReturnEmptyForUnknownName() {
      // when
      final var resolved = ClusterVersionCatalog.resolveByName("NOT_A_CAPABILITY");

      // then
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldBeCaseSensitive() {
      // when — names match enum constants exactly
      final var resolved = ClusterVersionCatalog.resolveByName("baseline");

      // then
      assertThat(resolved).isEmpty();
    }
  }

  // -- resolveByOrdinal -----------------------------------------------------

  @Nested
  class ResolveByOrdinalTests {

    @Test
    void shouldResolveBaselineOrdinal() {
      // when
      final var resolved =
          ClusterVersionCatalog.resolveByOrdinal(ClusterVersionCatalog.BASELINE_ORDINAL);

      // then
      assertThat(resolved).contains(Capability.BASELINE);
    }

    @Test
    void shouldResolveLaterOrdinal() {
      // when — PING_ADMISSION is at ordinal 10 (intentionally skipping 4–9 to model backports)
      final var resolved = ClusterVersionCatalog.resolveByOrdinal(10);

      // then
      assertThat(resolved).contains(Capability.PING_ADMISSION);
    }

    @Test
    void shouldReturnEmptyForUnclaimedOrdinal() {
      // when — gaps between claimed ordinals must surface as empty
      final var resolved = ClusterVersionCatalog.resolveByOrdinal(7);

      // then
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroOrdinal() {
      // when
      final var resolved = ClusterVersionCatalog.resolveByOrdinal(0);

      // then
      assertThat(resolved).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeOrdinal() {
      // when
      final var resolved = ClusterVersionCatalog.resolveByOrdinal(-1);

      // then
      assertThat(resolved).isEmpty();
    }
  }

  // -- catalog invariants ---------------------------------------------------

  @Nested
  class CatalogInvariantsTests {

    @Test
    void shouldExposeBaselineAsLowestOrdinal() {
      // then — BASELINE is the floor; nothing claims a lower ordinal
      for (final Capability c : Capability.values()) {
        assertThat(c.at()).isGreaterThanOrEqualTo(Capability.BASELINE.at());
      }
    }

    @Test
    void shouldExposeMaxCapabilityAsHighestOrdinal() {
      // when
      final Capability max = ClusterVersionCatalog.maxCapability();

      // then
      assertThat(max).isNotNull();
      for (final Capability c : Capability.values()) {
        assertThat(c.at()).isLessThanOrEqualTo(max.at());
      }
    }

    @Test
    void shouldNotReuseOrdinals() {
      // given — ordinals are a capability's identity and must be globally unique across history
      final var caps = Capability.values();

      // then
      for (int i = 0; i < caps.length; i++) {
        for (int j = i + 1; j < caps.length; j++) {
          assertThat(caps[i].at())
              .as(
                  "Capabilities %s and %s share ordinal %d",
                  caps[i].name(), caps[j].name(), caps[i].at())
              .isNotEqualTo(caps[j].at());
        }
      }
    }

    @Test
    void shouldExposeBaselineWithNoArtifacts() {
      // then — BASELINE is implicit; gating anything on it would defeat the purpose
      assertThat(Capability.BASELINE.appliers()).isEmpty();
      assertThat(Capability.BASELINE.commands()).isEmpty();
    }

    @Test
    void shouldRegisterApplierLookupsForEveryListedApplier() {
      // then — every applier listed on a capability is gated at that capability's ordinal
      for (final Capability c : Capability.values()) {
        for (final var id : c.appliers()) {
          assertThat(ClusterVersionCatalog.requiredOrdinalForApplier(id.intent(), id.version()))
              .hasValue(c.at());
          assertThat(
                  ClusterVersionCatalog.requiredOrdinalForApplierOrUngated(
                      id.intent(), id.version()))
              .isEqualTo(c.at());
        }
      }
    }

    @Test
    void shouldRegisterCommandLookupsForEveryListedCommand() {
      // then — every gated command listed on a capability is gated at that capability's ordinal
      for (final Capability c : Capability.values()) {
        for (final var id : c.commands()) {
          assertThat(ClusterVersionCatalog.requiredOrdinalForCommand(id.valueType(), id.intent()))
              .hasValue(c.at());
          assertThat(ClusterVersionCatalog.requiredOrdinalForCommand(id.intent())).hasValue(c.at());
          assertThat(ClusterVersionCatalog.requiredOrdinalForCommandOrUngated(id.intent()))
              .isEqualTo(c.at());
        }
      }
    }

    @Test
    void shouldReturnEmptyForUngatedAppliers() {
      // when — ClusterVersionIntent.APPLIED v1 predates ECV and is part of BASELINE
      final var required =
          ClusterVersionCatalog.requiredOrdinalForApplier(ClusterVersionIntent.APPLIED, 1);

      // then
      assertThat(required).isEmpty();
      assertThat(
              ClusterVersionCatalog.requiredOrdinalForApplierOrUngated(
                  ClusterVersionIntent.APPLIED, 1))
          .isEqualTo(-1);
    }

    @Test
    void shouldReturnEmptyForUngatedCommands() {
      // when — RAISE/APPLIED are part of the BASELINE protocol; only ECHO/PING are gated
      final var required =
          ClusterVersionCatalog.requiredOrdinalForCommand(
              ValueType.CLUSTER_VERSION, ClusterVersionIntent.RAISE);

      // then
      assertThat(required).isEmpty();
      assertThat(
              ClusterVersionCatalog.requiredOrdinalForCommandOrUngated(ClusterVersionIntent.RAISE))
          .isEqualTo(-1);
    }
  }

  // -- coverage checks ------------------------------------------------------

  @Nested
  class ApplierCoverageTests {

    @Test
    void shouldPassWhenAllCatalogAppliersRegistered() {
      // given — supply every applier the catalog lists
      final var registered = new HashSet<ApplierVersionId>();
      for (final Capability c : Capability.values()) {
        registered.addAll(c.appliers());
      }

      // when / then
      assertThatNoException()
          .isThrownBy(() -> ClusterVersionCatalog.validateApplierCoverage(registered));
    }

    @Test
    void shouldPassWhenExtraUngatedAppliersRegistered() {
      // given — registered set contains catalog entries plus unrelated v1 appliers
      final var registered = new HashSet<ApplierVersionId>();
      for (final Capability c : Capability.values()) {
        registered.addAll(c.appliers());
      }
      // multi-version applier that the catalog doesn't claim — legitimate ungated case
      registered.add(new ApplierVersionId(ClusterVersionIntent.APPLIED, 1));

      // when / then — reverse direction is not enforced
      assertThatNoException()
          .isThrownBy(() -> ClusterVersionCatalog.validateApplierCoverage(registered));
    }

    @Test
    void shouldFailWhenCatalogApplierMissing() {
      // given — an empty registered set, but the catalog declares appliers
      final Set<ApplierVersionId> registered = Set.of();

      // when / then — every catalog applier surfaces in the error
      assertThatThrownBy(() -> ClusterVersionCatalog.validateApplierCoverage(registered))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("applier coverage check failed")
          .hasMessageContaining("APPLIED_V2")
          .hasMessageContaining("DEMO_GATED_BRANCH");
    }

    @Test
    void shouldFailWithPointerWhenOneApplierMissing() {
      // given — drop exactly one catalog applier from the registered set
      final var registered = new HashSet<ApplierVersionId>();
      for (final Capability c : Capability.values()) {
        registered.addAll(c.appliers());
      }
      final var missing = new ApplierVersionId(ClusterVersionIntent.APPLIED, 3);
      registered.remove(missing);

      // when / then — the message names the missing capability so the dev knows where to look
      assertThatThrownBy(() -> ClusterVersionCatalog.validateApplierCoverage(registered))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DEMO_GATED_BRANCH")
          .hasMessageContaining("version=3");
    }
  }

  @Nested
  class CommandCoverageTests {

    @Test
    void shouldPassWhenAllCatalogCommandsRegistered() {
      // given
      final var registered = new HashSet<GatedCommandId>();
      for (final Capability c : Capability.values()) {
        registered.addAll(c.commands());
      }

      // when / then
      assertThatNoException()
          .isThrownBy(() -> ClusterVersionCatalog.validateCommandCoverage(registered));
    }

    @Test
    void shouldFailWhenCatalogCommandMissing() {
      // given
      final Set<GatedCommandId> registered = Set.of();

      // when / then
      assertThatThrownBy(() -> ClusterVersionCatalog.validateCommandCoverage(registered))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("command coverage check failed")
          .hasMessageContaining("ECHO")
          .hasMessageContaining("PING");
    }
  }

  @Nested
  class CatalogIntegrityTests {

    @Test
    void shouldPairValueTypeWithItsCanonicalIntentClass() {
      // when / then — every catalog command is well-paired (otherwise class init would have failed)
      for (final Capability c : Capability.values()) {
        for (final GatedCommandId id : c.commands()) {
          ClusterVersionCatalog.assertCommandPairing(id, c);
        }
      }
    }

    @Test
    void shouldRejectMismatchedValueTypeAndIntent() {
      // given — typo case: ValueType.JOB paired with a ClusterVersionIntent value
      final var bad = new GatedCommandId(ValueType.JOB, ClusterVersionIntent.ECHO);

      // when / then
      assertThatThrownBy(
              () -> ClusterVersionCatalog.assertCommandPairing(bad, Capability.DEMO_GATED_BRANCH))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ValueType.JOB")
          .hasMessageContaining("ClusterVersionIntent")
          .hasMessageContaining("JobIntent");
    }

    @Test
    void shouldRejectDuplicateApplierAcrossCapabilities() {
      // given — pretend two capabilities both list APPLIED v=2
      final var owners = new java.util.HashMap<ApplierVersionId, Capability>();
      final var id = new ApplierVersionId(ClusterVersionIntent.APPLIED, 2);
      ClusterVersionCatalog.assertUniqueApplierOwner(owners, id, Capability.APPLIED_V2);

      // when / then — the second owner is rejected with both capability names in the message
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertUniqueApplierOwner(
                      owners, id, Capability.DEMO_GATED_BRANCH))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("APPLIED_V2")
          .hasMessageContaining("DEMO_GATED_BRANCH")
          .hasMessageContaining("Each (intent, version) may belong to at most one capability");
    }

    @Test
    void shouldRejectDuplicateCommandAcrossCapabilities() {
      // given — pretend two capabilities both list the ECHO command
      final var owners = new java.util.HashMap<GatedCommandId, Capability>();
      final var id = new GatedCommandId(ValueType.CLUSTER_VERSION, ClusterVersionIntent.ECHO);
      ClusterVersionCatalog.assertUniqueCommandOwner(owners, id, Capability.DEMO_GATED_BRANCH);

      // when / then
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertUniqueCommandOwner(
                      owners, id, Capability.PING_ADMISSION))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DEMO_GATED_BRANCH")
          .hasMessageContaining("PING_ADMISSION")
          .hasMessageContaining("Each (valueType, intent) may belong to at most one capability");
    }

    @Test
    void shouldRejectDuplicateIntentAcrossCapabilities() {
      // given — pretend the same intent is gated by two different capabilities
      final var owners = new java.util.HashMap<Intent, Capability>();
      ClusterVersionCatalog.assertUniqueIntentOwner(
          owners, ClusterVersionIntent.ECHO, Capability.DEMO_GATED_BRANCH);

      // when / then
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertUniqueIntentOwner(
                      owners, ClusterVersionIntent.ECHO, Capability.PING_ADMISSION))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DEMO_GATED_BRANCH")
          .hasMessageContaining("PING_ADMISSION")
          .hasMessageContaining("single owner per intent");
    }

    @Test
    void shouldAllowSameCapabilityRevisitingSameIntent() {
      // given — the by-intent uniqueness check tolerates the same capability touching the same
      // intent multiple times (e.g., two commands of the same intent under one capability)
      final var owners = new java.util.HashMap<Intent, Capability>();
      ClusterVersionCatalog.assertUniqueIntentOwner(
          owners, ClusterVersionIntent.ECHO, Capability.DEMO_GATED_BRANCH);

      // when / then — same capability, no throw
      ClusterVersionCatalog.assertUniqueIntentOwner(
          owners, ClusterVersionIntent.ECHO, Capability.DEMO_GATED_BRANCH);
    }
  }

  @Nested
  class MinSupportedOrdinalTests {

    @Test
    void shouldAcceptActiveOrdinalEqualToFloor() {
      // when / then — the floor is the lowest tolerated value, not strict greater-than
      ClusterVersionCatalog.validateMinSupportedOrdinal(
          ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL);
    }

    @Test
    void shouldAcceptActiveOrdinalAboveFloor() {
      // when / then — a cluster that's raised past retired capabilities is fine
      ClusterVersionCatalog.validateMinSupportedOrdinal(
          ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL + 100);
    }

    @Test
    void shouldRejectActiveOrdinalBelowFloor() {
      // when / then — binary refuses to start; the message names both ordinals
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.validateMinSupportedOrdinal(
                      ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL - 1))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(String.valueOf(ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL - 1))
          .hasMessageContaining(String.valueOf(ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL));
    }

    @Test
    void shouldFloorAtBaselineForPocBinary() {
      // then — the PoC has retired nothing, so the floor stays at BASELINE
      assertThat(ClusterVersionCatalog.MIN_SUPPORTED_ORDINAL)
          .isEqualTo(ClusterVersionCatalog.BASELINE_ORDINAL);
    }
  }

  @Nested
  class AssertGatedByTests {

    @Test
    void shouldAcceptMatchingCapability() {
      // when / then — DEMO_GATED_BRANCH lists APPLIED v3
      assertThatNoException()
          .isThrownBy(
              () ->
                  ClusterVersionCatalog.assertGatedBy(
                      ClusterVersionIntent.APPLIED, 3, Capability.DEMO_GATED_BRANCH));
    }

    @Test
    void shouldRejectMismatchedCapability() {
      // when / then — APPLIED v3 is on DEMO_GATED_BRANCH, not APPLIED_V2
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertGatedBy(
                      ClusterVersionIntent.APPLIED, 3, Capability.APPLIED_V2))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("APPLIED_V2")
          .hasMessageContaining("version=3");
    }

    @Test
    void shouldRejectUnlistedPair() {
      // when / then — APPLIED v99 is in no Capability's appliers
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertGatedBy(
                      ClusterVersionIntent.APPLIED, 99, Capability.DEMO_GATED_BRANCH))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("DEMO_GATED_BRANCH")
          .hasMessageContaining("version=99");
    }

    @Test
    void shouldRejectBaselineAsGate() {
      // when / then — nothing should declare BASELINE as its gate (BASELINE has no artifacts)
      assertThatThrownBy(
              () ->
                  ClusterVersionCatalog.assertGatedBy(
                      ClusterVersionIntent.APPLIED, 3, Capability.BASELINE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BASELINE");
    }
  }
}
