/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.ecv.EcvApi.CheckResult;
import io.camunda.zeebe.broker.ecv.EcvApi.EcvState;
import io.camunda.zeebe.broker.ecv.EcvApi.MemberView;
import io.camunda.zeebe.broker.ecv.EcvApi.PartitionView;
import io.camunda.zeebe.broker.ecv.EcvApi.TenantView;
import io.camunda.zeebe.broker.ecv.EcvProtocol.PartitionProgress;
import io.camunda.zeebe.broker.ecv.EcvProtocol.ReadinessData;
import io.camunda.zeebe.broker.ecv.EcvProtocol.SupportedData;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The commit checks (R7) and the state read (design section 5), as a pure function of what the
 * system partition leader sees: the enabled set, the cluster configuration, the membership data and
 * the progress of the partitions.
 */
final class CommitEvaluator {

  private CommitEvaluator() {}

  /**
   * @param enabled the committed ECV
   * @param configuredBrokers the brokers of the cluster configuration
   * @param members the membership data of each reachable member, brokers and gateways
   * @param physicalTenants the partitions of each enabled physical tenant
   * @param progress the progress of each partition whose leader answered, by physical tenant
   */
  static Evaluation evaluate(
      final String answeredBy,
      final String commitMode,
      final CapabilitySet enabled,
      final Set<MemberId> configuredBrokers,
      final Map<MemberId, MemberData> members,
      final Map<String, Set<Integer>> physicalTenants,
      final Map<String, Map<Integer, PartitionProgress>> progress) {
    final var checks = new ArrayList<CheckResult>();
    final var memberViews = new TreeMap<String, MemberView>();

    // ready capabilities: the intersection over every broker and every running gateway (R3)
    CapabilitySet ready = null;
    final var unavailable = new ArrayList<String>();
    final var refused = new ArrayList<String>();
    final var notReady = new ArrayList<String>();
    for (final var broker : configuredBrokers) {
      final var data = members.get(broker);
      final var supported = data == null ? null : data.supported();
      if (data == null || supported == null) {
        unavailable.add(broker.id());
        memberViews.put(
            broker.id(), new MemberView("broker", false, List.of(), false, List.of(), Map.of()));
        continue;
      }
      final var set = supported.set();
      ready = ready == null ? set : ready.intersection(set);
      final var missing = enabled.minus(set);
      if (!missing.isEmpty()) {
        refused.add(broker.id() + " does not support " + missing);
      }
      final var readiness = currentReadiness(supported, data.readiness());
      if (readiness == null || readiness.components().isEmpty()) {
        notReady.add(broker.id() + ": no component reported its readiness");
      } else {
        readiness.components().entrySet().stream()
            .filter(component -> !component.getValue().ready())
            .forEach(
                component ->
                    notReady.add(
                        "%s: %s is not ready (%s)"
                            .formatted(
                                broker.id(), component.getKey(), component.getValue().reason())));
      }
      memberViews.put(
          broker.id(),
          new MemberView(
              "broker",
              true,
              set.names(),
              !missing.isEmpty(),
              missing.names(),
              readiness == null ? Map.of() : readiness.components()));
    }
    for (final var member : members.entrySet()) {
      if (configuredBrokers.contains(member.getKey()) || member.getValue().supported() == null) {
        continue;
      }
      // a member outside the cluster configuration that reports its capabilities: a gateway
      final var set = member.getValue().supported().set();
      ready = ready == null ? set : ready.intersection(set);
      final var missing = enabled.minus(set);
      memberViews.put(
          member.getKey().id(),
          new MemberView(
              "gateway", true, set.names(), !missing.isEmpty(), missing.names(), Map.of()));
    }
    final var readySet = ready == null ? CapabilitySet.EMPTY : ready;

    checks.add(
        check(
            "cluster-configuration-known",
            !configuredBrokers.isEmpty(),
            configuredBrokers.isEmpty()
                ? "The cluster configuration is not known yet"
                : "The cluster configuration has %d brokers".formatted(configuredBrokers.size()),
            "Wait until the broker that answers knows the cluster configuration."));
    checks.add(
        check(
            "brokers-available",
            unavailable.isEmpty(),
            unavailable.isEmpty()
                ? "All brokers of the cluster configuration are available"
                : "Brokers that are down or did not report their supported capabilities: "
                    + unavailable,
            "Start these brokers. A broker that is down blocks the commit (R4)."));
    checks.add(
        check(
            "no-refused-broker",
            refused.isEmpty(),
            refused.isEmpty() ? "No broker is refused" : "Refused brokers: " + refused,
            "Start the refused brokers with a binary that supports the enabled capabilities."));
    checks.add(
        check(
            "components-ready",
            notReady.isEmpty(),
            notReady.isEmpty() ? "All components are ready" : "Not ready: " + notReady,
            "Wait until the components report that they are ready."));
    final var missingPrerequisites = readySet.missingPrerequisites();
    checks.add(
        check(
            "prerequisites",
            missingPrerequisites.isEmpty(),
            missingPrerequisites.isEmpty()
                ? "The ready capabilities contain all prerequisites"
                : "Missing prerequisites: " + missingPrerequisites,
            "Upgrade the brokers to a binary that ships the prerequisites."));

    // progress of each enabled physical tenant (design section 4)
    final var tenantViews = new TreeMap<String, TenantView>();
    final var unavailablePartitions = new ArrayList<String>();
    boolean distributionPending = false;
    for (final var tenant : new TreeMap<>(physicalTenants).entrySet()) {
      final var partitionViews = new TreeMap<Integer, PartitionView>();
      boolean complete = true;
      for (final var partitionId : tenant.getValue()) {
        final var answer = progress.getOrDefault(tenant.getKey(), Map.of()).get(partitionId);
        final PartitionView view;
        if (answer == null) {
          unavailablePartitions.add(tenant.getKey() + "/" + partitionId);
          view = new PartitionView("UNKNOWN", List.of(), null);
        } else {
          final var applied = CapabilitySet.decode(answer.applied());
          if (applied.containsAll(enabled)) {
            view = new PartitionView("APPLIED", applied.names(), null);
          } else if (answer.rejected() != null
              && CapabilitySet.decode(answer.rejected()).containsAll(enabled)) {
            view = new PartitionView("REJECTED", applied.names(), answer.reason());
          } else {
            view = new PartitionView("PENDING", applied.names(), null);
          }
        }
        complete &= view.status().equals("APPLIED");
        partitionViews.put(partitionId, view);
      }
      distributionPending |= !complete;
      tenantViews.put(tenant.getKey(), new TenantView(complete, partitionViews));
    }
    checks.add(
        check(
            "physical-tenants-available",
            unavailablePartitions.isEmpty(),
            unavailablePartitions.isEmpty()
                ? "Each partition of each enabled physical tenant has a leader that answered"
                : "Partitions without a leader that answered: " + unavailablePartitions,
            "Wait until each partition has a leader."));

    final var commitAvailable = !enabled.containsAll(readySet);
    final var state =
        new EcvState(
            answeredBy,
            commitMode,
            enabled.names(),
            readySet.names(),
            commitAvailable,
            distributionPending,
            commitAvailable || distributionPending,
            List.copyOf(checks),
            memberViews,
            tenantViews);
    return new Evaluation(enabled, readySet, state);
  }

  private static @Nullable ReadinessData currentReadiness(
      final SupportedData supported, final @Nullable ReadinessData readiness) {
    // readiness from an earlier start of the broker is old data (design section 1)
    return readiness != null && readiness.instance().equals(supported.instance())
        ? readiness
        : null;
  }

  private static CheckResult check(
      final String name, final boolean passed, final String details, final String nextStep) {
    return new CheckResult(name, passed, details, passed ? null : nextStep);
  }

  record MemberData(@Nullable SupportedData supported, @Nullable ReadinessData readiness) {}

  record Evaluation(CapabilitySet enabled, CapabilitySet ready, EcvState state) {
    boolean commitAvailable() {
      return state.commitAvailable();
    }

    List<CheckResult> failedChecks() {
      return state.checks().stream().filter(check -> !check.passed()).toList();
    }

    /** The commit enables all ready capabilities and never removes one (R13, R14). */
    CapabilitySet decision() {
      return enabled.union(ready);
    }
  }
}
