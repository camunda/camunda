/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.clusterversion;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Single source of truth for Engine Capability Version (ECV) changes. Each {@link Capability} enum
 * constant <em>is</em> one capability step — its ordinal is the change's identity, and the constant
 * lists every artifact that change introduces (new applier versions, new admission-gated commands).
 *
 * <p>The deck's "an ordinal is a change's identity" rule is literal here: the operator-facing name
 * (the enum constant), the ordinal, the appliers, and the gated commands all live together. A
 * processor branches via {@code features.isActive(Capability.DEMO_GATED_BRANCH)}; the engine
 * command writer and the broker admission gate look up the same Capability by its artifacts.
 *
 * <p><b>Backport-friendliness.</b> Catalog entries do <em>not</em> carry per-capability release
 * version strings. That is deliberate: a string like {@code "8.11.0"} would be correct on {@code
 * main} but wrong on a {@code stable/8.10} cherry-pick, forcing every backport to edit the catalog.
 * Instead, the "raise to a release" shortcut ({@link #resolveByVersion(String)}) compares the
 * operator-supplied target version against the running binary's release (resolved at runtime via
 * {@link VersionUtil#getVersion()}) and admits the binary's maximum capability when the target is
 * at or above the running release. The catalog enum is byte-identical across branches; cherry-picks
 * are zero-touch.
 *
 * <p><b>How to add a new ordinal.</b>
 *
 * <ol>
 *   <li>Append a {@link Capability} enum constant at the bottom. Name it after the change it
 *       introduces; pass the ordinal it claims, a one-line description, and the artifacts.
 *   <li>For each {@link ApplierVersionId} listed, write the applier class and override its {@code
 *       gatedBy()} method to return the matching {@link Capability}. {@code EventAppliers.register}
 *       reads {@code gatedBy()} and cross-checks the catalog at boot — the catalog and the applier
 *       must agree, or registration throws.
 *   <li>For each {@link GatedCommandId} listed, ensure a processor for that command exists in
 *       {@code ClusterVersionProcessors}. {@code ClusterVersionGate} consults the catalog to know
 *       when admission is allowed.
 *   <li>If a processor needs to branch on the capability, it calls {@code
 *       features.isActive(Capability.NAME)}.
 * </ol>
 *
 * <p>No literal ordinal numbers should appear anywhere but in {@link Capability}.
 */
public final class ClusterVersionCatalog {

  /** Identifies a specific version of an event applier. */
  public record ApplierVersionId(Intent intent, int version) {}

  /** Identifies a specific command at the admission layer. */
  public record GatedCommandId(ValueType valueType, Intent intent) {}

  /**
   * The canonical, ordered history of Engine Capability Version steps. Each constant carries the
   * ordinal it claims, a one-line description, and the artifacts it introduces.
   *
   * <p>{@link #BASELINE} is the floor — every command and event that existed before ECV is
   * implicitly admitted under it. Fresh clusters start at the baseline ordinal (see {@code
   * ClusterVersionState.INITIAL_ORDINAL}); higher capabilities activate via {@code RAISE}.
   *
   * <p>New entries append to the bottom. Existing entries never move and never change.
   */
  public enum Capability {
    /**
     * The pre-ECV baseline. Every command and event that existed before the catalog was introduced
     * is implicitly part of this — they're not gated, and the cluster starts here on first install.
     * Suppressing the baseline would break the cluster; don't do it.
     */
    BASELINE(1, "Pre-ECV baseline — existing commands, events, and appliers", Set.of(), Set.of()),

    APPLIED_V2(
        2,
        "v2 of ClusterVersion APPLIED — applier carries the gated field",
        Set.of(new ApplierVersionId(ClusterVersionIntent.APPLIED, 2)),
        Set.of()),

    DEMO_GATED_BRANCH(
        3,
        "v3 of ClusterVersion APPLIED + ECHO command admission + processor branch",
        Set.of(new ApplierVersionId(ClusterVersionIntent.APPLIED, 3)),
        Set.of(new GatedCommandId(ValueType.CLUSTER_VERSION, ClusterVersionIntent.ECHO))),

    PING_ADMISSION(
        10,
        "PING command admission",
        Set.of(),
        Set.of(new GatedCommandId(ValueType.CLUSTER_VERSION, ClusterVersionIntent.PING))),

    /**
     * Gates the cancel-execution-listener path on process instance termination (PR #46880). When
     * inactive, the engine takes the legacy path: child termination leads directly to
     * finalizeTermination, no {@code CONTINUE_TERMINATING_ELEMENT} command is ever emitted, and no
     * cancel listener jobs are created — so the record stream matches a pre-PR broker, safe for
     * mid-rolling-upgrade leader changes. Activated → cancel listeners declared on the {@code
     * <process>} element run as job-worker jobs between child termination and the final terminated
     * state.
     */
    CANCEL_EXECUTION_LISTENER(
        11,
        "Cancel execution listeners on process instance termination",
        Set.of(),
        Set.of(
            new GatedCommandId(
                ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT))),

    /**
     * Gates the businessId-as-message-correlation-constraint feature (issue #51689). The cluster
     * may already be configured for businessId uniqueness via {@code
     * EngineConfiguration.isBusinessIdUniquenessEnabled()}, but the feature only fully activates
     * once the operator raises ECV to this ordinal. Below the gate, the engine takes the legacy
     * path: no cross-partition uniqueness handshake, no lock-release queries, businessId is carried
     * on records but never enforced. Above the gate, the cross-partition flow runs.
     *
     * <p>The gated commands are the entry points of the two new flows introduced by this feature:
     *
     * <ul>
     *   <li><b>Cross-partition handshake</b> ({@code MESSAGE_START_PROCESS_INSTANCE_REQUEST}) —
     *       {@code REQUEST} delegates message-start creation to {@code P_B = hash(businessId)}; the
     *       {@code START} / {@code REJECT_*} replies and the {@code SWEEP_EXPIRED_DEDUPS} trigger
     *       are all engine-internal follow-ups that must not slip past the gate either.
     *   <li><b>Pull-based lock release</b> ({@code MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE}) —
     *       {@code QUERY} asks {@code P_B} whether a cross-partition holder is still alive; {@code
     *       RELEASE} is the reply that triggers the lock-release on {@code P_K}.
     * </ul>
     */
    MESSAGE_BUSINESS_ID_CORRELATION(
        12,
        "Business ID as additional message correlation constraint (cross-partition handshake)",
        Set.of(),
        Set.of(
            new GatedCommandId(
                ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
                MessageStartProcessInstanceRequestIntent.REQUEST),
            new GatedCommandId(
                ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
                MessageStartProcessInstanceRequestIntent.START),
            new GatedCommandId(
                ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
                MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS),
            new GatedCommandId(
                ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
                MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION),
            new GatedCommandId(
                ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
                MessageStartProcessInstanceRequestIntent.SWEEP_EXPIRED_DEDUPS),
            new GatedCommandId(
                ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE,
                MessageStartCorrelationKeyLockReleaseIntent.QUERY),
            new GatedCommandId(
                ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE,
                MessageStartCorrelationKeyLockReleaseIntent.RELEASE))),

    /**
     * Gates the job-prioritization feature (issue #50567). Three sub-features, all routed through
     * applier-version selection so the gate composes naturally with the engine's existing
     * versioned-applier mechanism:
     *
     * <ul>
     *   <li><b>Job creation with priority-CF activation</b> — {@code JobCreatedV3Applier} is the
     *       priority-aware path. It calls {@code MutableJobState.createWithPriorityActivation},
     *       which writes the job into the {@code JOB_ACTIVATABLE_BY_PRIORITY} column family. Below
     *       the gate the write side picks v=2 instead, which routes through {@code
     *       MutableJobState.create} → legacy {@code JOB_ACTIVATABLE} column family. A low-ECV
     *       cluster's state therefore matches a pre-PR broker exactly.
     *   <li><b>Priority field on JobRecord at creation</b> — {@code BpmnJobBehavior} only evaluates
     *       the BPMN priority expression when this capability is active; below the gate the field
     *       is forced to 0 so the v=2 record stream is byte-identical to pre-PR.
     *   <li><b>Priority update API</b> — {@code JobIntent.UPDATE} command (pre-existing intent)
     *       carrying a PRIORITY changeset emits a new {@code PRIORITY_UPDATED} event. The processor
     *       branch in {@code JobUpdateProcessor} consults this capability and rejects the command
     *       when the gate is closed; the {@code PRIORITY_UPDATED} v=1 applier is listed here so the
     *       event writer's {@code selectVersionFor} returns -1 below the gate as defense-in-depth.
     * </ul>
     */
    JOB_PRIORITIZATION(
        13,
        "Job-level priority field, priority-ordered activation, and priority update API",
        Set.of(
            new ApplierVersionId(JobIntent.PRIORITY_UPDATED, 1),
            new ApplierVersionId(JobIntent.CREATED, 3)),
        Set.of()),

    /**
     * Retroactive gate on the PR #50012 bug fix that added {@code recordRPIMetric} to {@code
     * ProcessEventTriggeringV2Applier} so process instances started via message/timer/signal/
     * conditional events are counted toward RPI metrics. The fix shipped to 8.8.x but the backport
     * to 8.9 was missed; an 8.9 broker taking over a stream that already contained {@code
     * ProcessEvent.TRIGGERING v=2} records had no v=2 applier, broke replay, and blocked the
     * upgrade.
     *
     * <p>Gating the applier here teaches the pattern: had this capability existed when the fix was
     * merged, 8.8 brokers would have shipped the v=2 applier code but the write-side {@code
     * selectVersionFor(TRIGGERING)} would have refused to stamp v=2 records until the operator
     * raised ECV. An 8.9 cluster (whether or not it had the v=2 applier code) could replay any
     * pre-raise log because only v=1 records existed. The fix becomes opt-in coordinated activation
     * rather than implicit write-on-deploy.
     *
     * <p>Subsequent {@code TRIGGERING v=3} (PR #52727) is left in {@code BASELINE_APPLIERS} for
     * now; the same gating pattern should be applied there in a follow-up if its release history
     * had similar coordination gaps. The exercise here is documenting how a bug fix touching the
     * stream protocol should be gated <em>at merge time</em>, not paper over what already happened.
     */
    EVENT_START_RPI_METRIC(
        14,
        "RPI metric tracking on event-driven process starts (retro-gate on PR #50012)",
        Set.of(new ApplierVersionId(ProcessEventIntent.TRIGGERING, 2)),
        Set.of()),

    /**
     * The proper follow-up to {@link #EVENT_START_RPI_METRIC} from PR #52727: instead of having
     * {@code ProcessEventTriggeringV2Applier} record the RPI metric inline, the engine now emits a
     * {@code ProcessInstanceCreation.CREATED} event for every event-driven start, and the
     * pre-existing {@code ProcessInstanceCreationCreatedV2Applier} records the metric from that.
     * The two protocol-level changes that need coordinated activation:
     *
     * <ul>
     *   <li>{@code ProcessEventTriggeringV3Applier} replaces v=2 — its body no longer touches the
     *       usage-metric state, deliberately moving the responsibility to PI creation. Gating it
     *       here under a higher ordinal than {@link #EVENT_START_RPI_METRIC} preserves the partial
     *       ordering: an operator must raise through v=2 before reaching v=3.
     *   <li>{@code EventHandle.activateProcessInstanceForStartEvent} now emits {@code
     *       PROCESS_INSTANCE_CREATION:CREATED} as a follow-up event. This emission is behaviorally
     *       paired with v=3 — emitting it while v=2 is still the selected applier would
     *       double-count the RPI metric. The runtime check in {@code EventHandle} gates the
     *       emission on this capability so it stays off until v=3 is selectable.
     * </ul>
     *
     * <p>Combined, raising ECV to this ordinal flips both the applier-selection (v=2 → v=3) and the
     * emission (off → on) atomically — no double-counting window in the middle, no single-side
     * activation.
     */
    PI_CREATED_FOR_EVENT_STARTS(
        15,
        "Emit PROCESS_INSTANCE_CREATION:CREATED for event-driven starts + drop metric from "
            + "TRIGGERING applier (PR #52727)",
        Set.of(new ApplierVersionId(ProcessEventIntent.TRIGGERING, 3)),
        Set.of());

    private final int at;
    private final String description;
    private final Set<ApplierVersionId> appliers;
    private final Set<GatedCommandId> commands;

    Capability(
        final int at,
        final String description,
        final Set<ApplierVersionId> appliers,
        final Set<GatedCommandId> commands) {
      this.at = at;
      this.description = description;
      this.appliers = appliers;
      this.commands = commands;
    }

    /** The ECV ordinal this capability claims. */
    public int at() {
      return at;
    }

    public String description() {
      return description;
    }

    public Set<ApplierVersionId> appliers() {
      return appliers;
    }

    public Set<GatedCommandId> commands() {
      return commands;
    }
  }

  /** Convenience constant — the ordinal a fresh cluster starts at. */
  public static final int BASELINE_ORDINAL = Capability.BASELINE.at();

  /**
   * The lowest ECV ordinal this binary can correctly operate at. Bumped <em>by hand</em> when a
   * capability's branching is retired — i.e. the {@code features.isActive(Capability.X)} checks are
   * deleted and the new behavior becomes unconditional. After that deletion the binary can no
   * longer correctly run on a cluster whose active ordinal is below {@code Capability.X.at()}; boot
   * fails fast with {@link #validateMinSupportedOrdinal(int)} rather than corrupting state.
   *
   * <p>Per-branch by design: each branch sets its own floor. {@code main} can advance the floor as
   * features get adopted everywhere on the main line; stable branches keep the lower floor because
   * their fleet hasn't necessarily reached the same level.
   *
   * <p>Starts at {@link #BASELINE_ORDINAL} (nothing retired yet). A fresh PoC binary supports every
   * ordinal the catalog knows about.
   */
  public static final int MIN_SUPPORTED_ORDINAL = BASELINE_ORDINAL;

  // Precomputed indexes — populated once at class init from Capability.values(). All hot-path
  // lookups resolve via HashMap.get rather than streaming the constants.
  private static final Map<ApplierVersionId, Integer> APPLIER_REQS;
  private static final Map<GatedCommandId, Integer> COMMAND_REQS;
  private static final Map<Intent, Integer> COMMAND_REQS_BY_INTENT;
  private static final Map<String, Capability> CAPABILITY_BY_NAME;
  private static final Capability MAX_CAPABILITY;

  static {
    final var applierReqs = new HashMap<ApplierVersionId, Integer>();
    final var commandReqs = new HashMap<GatedCommandId, Integer>();
    final var commandReqsByIntent = new HashMap<Intent, Integer>();
    final var byName = new HashMap<String, Capability>();
    final var applierOwner = new HashMap<ApplierVersionId, Capability>();
    final var commandOwner = new HashMap<GatedCommandId, Capability>();
    final var intentOwner = new HashMap<Intent, Capability>();
    Capability max = null;
    for (final Capability cap : Capability.values()) {
      byName.put(cap.name(), cap);
      if (max == null || cap.at() > max.at()) {
        max = cap;
      }
      for (final var id : cap.appliers()) {
        // Reject duplicate (intent, version) across capabilities — otherwise the requirement map
        // last-write-wins silently and an applier's effective gate becomes load-order-dependent.
        assertUniqueApplierOwner(applierOwner, id, cap);
        applierReqs.put(id, cap.at());
      }
      for (final var id : cap.commands()) {
        // Integrity: the intent must actually belong to the named ValueType.
        assertCommandPairing(id, cap);
        // Reject duplicate (valueType, intent) across capabilities.
        assertUniqueCommandOwner(commandOwner, id, cap);
        // Same intent under two ValueTypes would also fail the cross-capability uniqueness
        // because the by-intent lookup would last-write-wins.
        assertUniqueIntentOwner(intentOwner, id.intent(), cap);
        commandReqs.put(id, cap.at());
        commandReqsByIntent.put(id.intent(), cap.at());
      }
    }
    APPLIER_REQS = Map.copyOf(applierReqs);
    COMMAND_REQS = Map.copyOf(commandReqs);
    COMMAND_REQS_BY_INTENT = Map.copyOf(commandReqsByIntent);
    CAPABILITY_BY_NAME = Map.copyOf(byName);
    MAX_CAPABILITY = max;
  }

  /**
   * Throw if {@code id.intent()} doesn't belong to {@code id.valueType()} per the protocol's
   * canonical {@code ValueType → IntentClass} map. Catches typos like {@code
   * GatedCommandId(ValueType.JOB, ClusterVersionIntent.ECHO)} at catalog class init.
   */
  static void assertCommandPairing(final GatedCommandId id, final Capability cap) {
    final Class<? extends Intent> expectedIntentClass = Intent.fromValueType(id.valueType());
    if (id.intent().getClass() != expectedIntentClass) {
      throw new IllegalStateException(
          "ClusterVersionCatalog integrity violation: command "
              + id
              + " in Capability."
              + cap.name()
              + " pairs ValueType."
              + id.valueType().name()
              + " with an intent from "
              + id.intent().getClass().getSimpleName()
              + ", but that ValueType is owned by "
              + expectedIntentClass.getSimpleName()
              + ".");
    }
  }

  static void assertUniqueApplierOwner(
      final Map<ApplierVersionId, Capability> owners,
      final ApplierVersionId id,
      final Capability cap) {
    final var prev = owners.put(id, cap);
    if (prev != null) {
      throw new IllegalStateException(
          "ClusterVersionCatalog integrity violation: applier "
              + id
              + " is listed by both Capability."
              + prev.name()
              + " and Capability."
              + cap.name()
              + ". Each (intent, version) may belong to at most one capability.");
    }
  }

  static void assertUniqueCommandOwner(
      final Map<GatedCommandId, Capability> owners, final GatedCommandId id, final Capability cap) {
    final var prev = owners.put(id, cap);
    if (prev != null) {
      throw new IllegalStateException(
          "ClusterVersionCatalog integrity violation: command "
              + id
              + " is listed by both Capability."
              + prev.name()
              + " and Capability."
              + cap.name()
              + ". Each (valueType, intent) may belong to at most one capability.");
    }
  }

  static void assertUniqueIntentOwner(
      final Map<Intent, Capability> owners, final Intent intent, final Capability cap) {
    final var prev = owners.put(intent, cap);
    if (prev != null && prev != cap) {
      throw new IllegalStateException(
          "ClusterVersionCatalog integrity violation: intent "
              + intent
              + " is gated by both Capability."
              + prev.name()
              + " and Capability."
              + cap.name()
              + ". The by-intent admission lookup requires a single owner per intent.");
    }
  }

  private ClusterVersionCatalog() {}

  /** ECV ordinal gating an applier version, or empty if not gated. */
  public static OptionalInt requiredOrdinalForApplier(final Intent intent, final int version) {
    final var ordinal = APPLIER_REQS.get(new ApplierVersionId(intent, version));
    return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
  }

  /** Hot-path variant — returns -1 if not gated. */
  public static int requiredOrdinalForApplierOrUngated(final Intent intent, final int version) {
    final var ordinal = APPLIER_REQS.get(new ApplierVersionId(intent, version));
    return ordinal == null ? -1 : ordinal;
  }

  /** ECV ordinal gating a command at the admission layer, or empty if not gated. */
  public static OptionalInt requiredOrdinalForCommand(
      final ValueType valueType, final Intent intent) {
    final var ordinal = COMMAND_REQS.get(new GatedCommandId(valueType, intent));
    return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
  }

  /** Intent-only variant — used by the engine command writer. */
  public static OptionalInt requiredOrdinalForCommand(final Intent intent) {
    final var ordinal = COMMAND_REQS_BY_INTENT.get(intent);
    return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
  }

  /** Hot-path variant — returns -1 if not gated. */
  public static int requiredOrdinalForCommandOrUngated(final Intent intent) {
    final var ordinal = COMMAND_REQS_BY_INTENT.get(intent);
    return ordinal == null ? -1 : ordinal;
  }

  /** Look up a {@link Capability} by its name (operator-facing identifier). */
  public static Capability capabilityByName(final String name) {
    return CAPABILITY_BY_NAME.get(name);
  }

  /** The highest-ordinal capability known to this binary. */
  public static Capability maxCapability() {
    return MAX_CAPABILITY;
  }

  /**
   * Resolve a release version string (e.g. {@code "8.10.1"}) to the highest capability this binary
   * knows about, provided the supplied target is at or above the binary's own release version.
   *
   * <p>This is the MongoDB-FCV-style "raise to a release" shortcut — operators say "activate
   * everything available in 8.10.5" instead of typing an ordinal. The semantics are intentionally
   * coarse: catalog entries are branch-identical (no per-capability version strings), so the only
   * version-granular signal available is the running binary's release.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If the running binary's release cannot be parsed as semver (e.g. {@code "development"} in
   *       an IDE/test run), every well-formed target version resolves to the binary's max
   *       capability — useful for dev environments.
   *   <li>If the target version is at or above the binary's release, returns the max capability.
   *   <li>If the target version is below the binary's release, returns empty — the operator is
   *       asking to land short of what this binary supports; for finer control they should use the
   *       capability name or raw ordinal endpoints.
   *   <li>If the target version is not parseable as semver, returns empty.
   * </ul>
   */
  public static Optional<Capability> resolveByVersion(final String version) {
    return resolveByVersion(version, VersionUtil.getVersion());
  }

  /** Package-private overload for tests — lets the caller supply the binary's build version. */
  static Optional<Capability> resolveByVersion(final String target, final String buildVersion) {
    final var parsedTarget = SemanticVersion.parse(target);
    if (parsedTarget.isEmpty()) {
      return Optional.empty();
    }
    final var parsedBuild = SemanticVersion.parse(buildVersion);
    if (parsedBuild.isEmpty()) {
      // Dev/test run where the binary has no semver tag — accept any well-formed target.
      return Optional.ofNullable(MAX_CAPABILITY);
    }
    if (parsedTarget.get().compareTo(parsedBuild.get()) < 0) {
      return Optional.empty();
    }
    return Optional.ofNullable(MAX_CAPABILITY);
  }

  /** Resolve an explicit capability name (case-sensitive) to its constant. */
  public static Optional<Capability> resolveByName(final String name) {
    return Optional.ofNullable(CAPABILITY_BY_NAME.get(name));
  }

  /** Resolve a raw ordinal to the capability that claims it (if any). */
  public static Optional<Capability> resolveByOrdinal(final int ordinal) {
    for (final Capability candidate : Capability.values()) {
      if (candidate.at() == ordinal) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  /**
   * Verify the supplied set of registered applier IDs covers every applier the catalog declares.
   *
   * <p>This is the forward-direction boot check: every {@link ApplierVersionId} listed by any
   * {@link Capability#appliers()} must be present in {@code registeredApplierIds}, or the engine
   * cannot honor that catalog entry (the applier would be selected and immediately fall off the
   * map). Catches the "added a {@link Capability} entry but forgot to wire the applier" mistake at
   * startup, before any traffic.
   *
   * <p>The reverse direction — "the applier is registered, but the catalog doesn't list it" — is
   * intentionally not enforced here, because the multi-version applier mechanism predates ECV and
   * many existing {@code v ≥ 2} appliers are legitimately ungated. Use the
   * capability-typed-register overload at the call site to assert that direction.
   *
   * @throws IllegalStateException if any catalog applier is unregistered.
   */
  public static void validateApplierCoverage(final Set<ApplierVersionId> registeredApplierIds) {
    final List<String> missing = new ArrayList<>();
    for (final Capability c : Capability.values()) {
      for (final ApplierVersionId id : c.appliers()) {
        if (!registeredApplierIds.contains(id)) {
          missing.add(
              "Capability."
                  + c.name()
                  + " declares applier "
                  + id
                  + " but no applier is registered for that (intent, version).");
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "ClusterVersionCatalog applier coverage check failed:\n  - "
              + String.join("\n  - ", missing));
    }
  }

  /**
   * Verify the supplied set of registered command IDs covers every command the catalog declares.
   *
   * <p>Symmetric to {@link #validateApplierCoverage(Set)} for the admission-gated commands. The
   * reverse direction (an unregistered intent slipping past admission) is the harder case — a
   * brand-new intent introduced after ECV without a catalog entry would be silently un-gated.
   * Resolving that would require enumerating every pre-ECV intent as BASELINE, which is deferred as
   * future work.
   *
   * @throws IllegalStateException if any catalog command lacks a processor in the supplied set.
   */
  public static void validateCommandCoverage(final Set<GatedCommandId> registeredCommandIds) {
    final List<String> missing = new ArrayList<>();
    for (final Capability c : Capability.values()) {
      for (final GatedCommandId id : c.commands()) {
        if (!registeredCommandIds.contains(id)) {
          missing.add(
              "Capability."
                  + c.name()
                  + " declares command "
                  + id
                  + " but no processor is wired for that (valueType, intent).");
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "ClusterVersionCatalog command coverage check failed:\n  - "
              + String.join("\n  - ", missing));
    }
  }

  /**
   * Fail-fast assertion that the cluster's active ordinal is at least {@link
   * #MIN_SUPPORTED_ORDINAL}. Called at engine boot, after the active ordinal becomes readable from
   * state. Refusing to start is the safe move — a binary that has retired branches for a capability
   * cannot correctly process a cluster still below that ordinal.
   *
   * @throws IllegalStateException if {@code activeOrdinal < MIN_SUPPORTED_ORDINAL}.
   */
  public static void validateMinSupportedOrdinal(final int activeOrdinal) {
    if (activeOrdinal < MIN_SUPPORTED_ORDINAL) {
      throw new IllegalStateException(
          "Cluster's active ECV ordinal "
              + activeOrdinal
              + " is below this binary's minimum supported ordinal "
              + MIN_SUPPORTED_ORDINAL
              + ". This binary has retired branching code that the cluster's state still expects."
              + " Raise the cluster's ECV (or downgrade the binary) before resuming.");
    }
  }

  /**
   * Assert that {@code (intent, version)} is listed under {@code gatedBy}'s {@link
   * Capability#appliers()}. Intended for use at applier-registration sites that want to be explicit
   * about <em>which</em> capability gates them — the catalog stays the single source of truth, but
   * the call site declares its expectation and the runtime verifies they match.
   *
   * @throws IllegalArgumentException if the catalog doesn't list this {@code (intent, version)}
   *     under {@code gatedBy}.
   */
  public static void assertGatedBy(
      final Intent intent, final int version, final Capability gatedBy) {
    final var id = new ApplierVersionId(intent, version);
    if (!gatedBy.appliers().contains(id)) {
      throw new IllegalArgumentException(
          "Applier registration for "
              + id
              + " names Capability."
              + gatedBy.name()
              + " as its gate, but the catalog does not list this (intent, version) there."
              + " Add the ApplierVersionId to Capability."
              + gatedBy.name()
              + ".appliers() in ClusterVersionCatalog (or use the un-gated 3-arg register if this"
              + " applier is record-version-only and not ECV-gated).");
    }
  }
}
