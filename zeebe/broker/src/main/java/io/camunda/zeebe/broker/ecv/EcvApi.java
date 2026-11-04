/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.camunda.zeebe.broker.ecv.UpgradeReadinessRegistry.ComponentReadiness;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The responses of the ECV commit and the ECV state read (design section 5). */
public final class EcvApi {
  private EcvApi() {}

  public record CommitResponse(
      CommitStatus status,
      String answeredBy,
      List<String> enabled,
      List<CheckResult> failedChecks) {}

  public enum CommitStatus {
    /** The decision is in the system partition. The physical tenants apply it afterwards. */
    COMMITTED,
    /** All ready capabilities are already enabled; nothing changed (R17). */
    NO_COMMIT_AVAILABLE,
    /** A commit check failed (R8). */
    REJECTED,
    /** No system partition leader answered; try again. */
    UNAVAILABLE
  }

  /**
   * The ECV state as the system partition leader sees it.
   *
   * @param enabled the committed ECV
   * @param ready the capabilities that all brokers and running gateways support
   * @param commitAvailable the ready capabilities contain a capability that is not enabled
   * @param distributionPending a partition of an enabled physical tenant did not apply the enabled
   *     capabilities yet
   * @param commitPending a commit is available or the distribution is pending (R6)
   */
  public record EcvState(
      String answeredBy,
      String commitMode,
      List<String> enabled,
      List<String> ready,
      boolean commitAvailable,
      boolean distributionPending,
      boolean commitPending,
      List<CheckResult> checks,
      Map<String, MemberView> members,
      Map<String, TenantView> physicalTenants) {}

  /** One commit check (R7) and its result, with the next step for the user if it failed (R8). */
  public record CheckResult(
      String name, boolean passed, String details, @Nullable String nextStep) {}

  /**
   * @param refused the member does not support all enabled capabilities (design section 6)
   * @param unsupportedEnabled the enabled capabilities that the member does not support
   */
  public record MemberView(
      String type,
      boolean available,
      List<String> supported,
      boolean refused,
      List<String> unsupportedEnabled,
      Map<String, ComponentReadiness> readiness) {}

  public record TenantView(boolean complete, Map<Integer, PartitionView> partitions) {}

  /**
   * @param status APPLIED, PENDING, REJECTED or UNKNOWN (no leader answered)
   */
  public record PartitionView(
      String status, List<String> applied, @Nullable String rejectionReason) {}
}
