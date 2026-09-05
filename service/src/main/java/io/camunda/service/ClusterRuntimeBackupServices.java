/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.PhysicalTenantFanOut.Outcome;
import io.camunda.service.RuntimeBackupServices.RuntimeBackupState;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PagedListing;
import io.camunda.zeebe.backup.client.api.State;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runtime (primary-storage) backups across every physical tenant of the cluster, or across the one
 * a request narrows to (ADR 003 D2 and D4, {@code
 * docs/adr/management/003-physical-tenant-management-endpoint-inventory.md}).
 *
 * <p>Unlike {@link RuntimeBackupServices} this takes no {@code CamundaAuthentication} and performs
 * no per-tenant authorization: per ADR 002 D4 the cluster-admin security chain is the only gate,
 * and taking no authentication at all makes that structurally true rather than a convention a later
 * edit can break.
 *
 * <p>A cluster-wide backup is a <em>set of independent per-tenant backups</em>, not an atomic
 * snapshot of the cluster, and that shapes every operation here:
 *
 * <ul>
 *   <li><b>Triggering is all-or-error, and never silent.</b> There is nothing to roll back a
 *       triggered backup with, so instead of hiding a partial trigger behind an error, {@link
 *       #takeBackup} always reports what each targeted tenant did — including the id to monitor or
 *       delete the backups that <em>are</em> running by. Only the caller can decide what to do with
 *       them.
 *   <li><b>Absence is a successful observation.</b> A backup that exists on one physical tenant and
 *       not another is normal, so a tenant that was reached and holds nothing reports {@link
 *       State#DOES_NOT_EXIST} and folds into an {@code INCOMPLETE} cluster-wide state rather than
 *       failing the request.
 *   <li><b>Every other failure is all-or-nothing.</b> A tenant whose state cannot be observed fails
 *       the whole request; a caller works around a broken tenant by narrowing to the others.
 * </ul>
 *
 * <p>{@link BackupApi} is asynchronous, so the fan-out needs no executor of its own: it starts one
 * request per targeted tenant and waits for all of them.
 */
@NullMarked
public final class ClusterRuntimeBackupServices {

  /**
   * The {@code BackupIdPrefix} schema of the REST contract, enforced before any tenant is asked.
   */
  private static final Pattern BACKUP_ID_PREFIX = Pattern.compile("^\\d*\\*$");

  private final SortedMap<String, PhysicalTenantBackupPort> portsByPhysicalTenant;

  public ClusterRuntimeBackupServices(final Collection<PhysicalTenantBackupPort> ports) {
    // Sorted so every response lists physical tenants in the same, assertable order.
    portsByPhysicalTenant =
        new TreeMap<>(
            ports.stream()
                .collect(
                    Collectors.toMap(
                        PhysicalTenantBackupPort::physicalTenantId, Function.identity())));
  }

  /**
   * Triggers a backup on every targeted physical tenant, and reports what each one did.
   *
   * <p>The returned future completes successfully even when tenants failed — a partial trigger is
   * an outcome to report, not an error to swallow. {@link
   * ClusterRuntimeBackupTaken#failureStatus()} carries the status the request should answer with,
   * and is {@code null} only when every targeted tenant was triggered. A request rejected before
   * any tenant was triggered fails the future instead, so a caller can tell "nothing is running"
   * from "some of it is".
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   * @param backupId the id to use on every targeted tenant, or {@code null} to have every targeted
   *     tenant generate its own
   */
  public CompletableFuture<ClusterRuntimeBackupTaken> takeBackup(
      final @Nullable String physicalTenantId, final @Nullable Long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          requireBackupIdMatchesEveryTenantsMode(targets, backupId);
          return onEveryTenant(targets, target -> take(target, backupId))
              .thenApply(outcomes -> toTaken(outcomes, backupId));
        });
  }

  /**
   * Reports what every targeted physical tenant holds for the given backup id, plus the state
   * folded over all of them. Fails with {@link Status#NOT_FOUND} only when no targeted tenant holds
   * it.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<ClusterRuntimeBackup> getBackup(
      final @Nullable String physicalTenantId, final long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          requirePositiveBackupId(backupId);
          return onEveryTenant(
                  targets, target -> target.api().getStatus(target.physicalTenantId(), backupId))
              .thenApply(PhysicalTenantFanOut::requireEveryTenant)
              .thenApply(
                  statuses -> {
                    final var perTenant = zip(targets, statuses, PhysicalTenantRuntimeBackup::new);
                    if (perTenant.stream()
                        .allMatch(tenant -> tenant.backup().status() == State.DOES_NOT_EXIST)) {
                      throw noTenantHolds(backupId, targets);
                    }
                    return toClusterBackup(backupId, perTenant);
                  });
        });
  }

  /**
   * Lists the backups of every targeted physical tenant, grouped by backup id, most recent id
   * first. Every group reports every targeted tenant, so a backup only some tenants hold folds to
   * {@code INCOMPLETE} here just as it does on a single-id read.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<List<ClusterRuntimeBackup>> listBackups(
      final @Nullable String physicalTenantId, final @Nullable String prefix) {
    return listBackups(physicalTenantId, prefix, null, null);
  }

  /**
   * Lists one page of the backups of every targeted physical tenant, grouped by backup id, most
   * recent id first. With a {@code limit} the result holds at most {@code limit} backup ids, whose
   * last one is the {@code before} cursor of the next page; a page shorter than {@code limit} is
   * the last one. Every tenant is asked for the same page, and only ids every tenant has been
   * enumerated past are reported, see {@link PagedListing#safeBound}.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<List<ClusterRuntimeBackup>> listBackups(
      final @Nullable String physicalTenantId,
      final @Nullable String prefix,
      final @Nullable Long before,
      final @Nullable Integer limit) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          final var queried = requireValidPrefix(prefix);
          final var page = requireValidPage(before, limit);
          return onEveryTenant(
                  targets,
                  target ->
                      target
                          .api()
                          .listBackups(
                              target.physicalTenantId(), queried, page.before(), page.limit()))
              .thenApply(PhysicalTenantFanOut::requireEveryTenant)
              .thenApply(perTenant -> groupByBackupId(targets, perTenant, page.limit()));
        });
  }

  /**
   * Deletes the backup from every targeted physical tenant. A tenant that does not hold it has
   * already reached the requested end state, which is why this reports no per-tenant outcome — the
   * same reason {@code DELETE /v2/backups/runtime/{backupId}} answers 204 for an unknown id.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<Void> deleteBackup(
      final @Nullable String physicalTenantId, final long backupId) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          requirePositiveBackupId(backupId);
          return onEveryTenant(
                  targets, target -> target.api().deleteBackup(target.physicalTenantId(), backupId))
              .thenApply(ClusterRuntimeBackupServices::everyTenantSucceeded);
        });
  }

  /**
   * Reports the checkpoint and backup state of every targeted physical tenant. Like {@link
   * RuntimeBackupServices#getRuntimeState}, this fails the whole request if either sub-request
   * fails on any tenant, instead of contributing an empty section a caller could not tell apart
   * from "nothing to report yet".
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<ClusterRuntimeBackupStates> getRuntimeState(
      final @Nullable String physicalTenantId) {
    return runtimeStates(physicalTenantId, ClusterRuntimeBackupServices::readRuntimeState);
  }

  /**
   * Force-writes the checkpoint and backup metadata of every targeted physical tenant to its backup
   * store, then reports the updated state.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<ClusterRuntimeBackupStates> syncRuntimeState(
      final @Nullable String physicalTenantId) {
    return runtimeStates(physicalTenantId, ClusterRuntimeBackupServices::writeRuntimeState);
  }

  /**
   * Resets the runtime backup state of every targeted physical tenant.
   *
   * @param physicalTenantId the single tenant to target, or {@code null} for every tenant
   */
  public CompletableFuture<Void> deleteRuntimeState(final @Nullable String physicalTenantId) {
    return validated(
        () ->
            onEveryTenant(
                    targets(physicalTenantId),
                    target -> target.api().deleteRuntimeState(target.physicalTenantId()))
                .thenApply(ClusterRuntimeBackupServices::everyTenantSucceeded));
  }

  private CompletableFuture<ClusterRuntimeBackupStates> runtimeStates(
      final @Nullable String physicalTenantId,
      final Function<PhysicalTenantBackupPort, CompletionStage<RuntimeBackupState>> perTenant) {
    return validated(
        () -> {
          final var targets = targets(physicalTenantId);
          return onEveryTenant(targets, perTenant)
              .thenApply(PhysicalTenantFanOut::requireEveryTenant)
              .thenApply(
                  states ->
                      new ClusterRuntimeBackupStates(
                          zip(targets, states, PhysicalTenantRuntimeBackupState::new)));
        });
  }

  /**
   * Resolves the physical tenants a request targets, rejecting an id this cluster does not know
   * before any tenant is contacted — so an unknown id is never mistaken for a tenant that failed.
   */
  private List<PhysicalTenantBackupPort> targets(final @Nullable String physicalTenantId) {
    if (physicalTenantId == null) {
      return List.copyOf(portsByPhysicalTenant.values());
    }
    final var port = portsByPhysicalTenant.get(physicalTenantId);
    if (port == null) {
      throw new ServiceException(
          "Expected to target physical tenant '%s', but this cluster only has %s"
              .formatted(physicalTenantId, portsByPhysicalTenant.keySet()),
          Status.NOT_FOUND);
    }
    return List.of(port);
  }

  /**
   * Rejects a backup id that not every targeted tenant can accept, before anything is triggered.
   *
   * <p>Backup id generation is configured per physical tenant, so a cluster can mix the two modes.
   * There is no honest way to serve such a cluster in one call — an id can be supplied to all
   * tenants or to none — so the request names the tenants standing in the way and the caller drives
   * them one at a time through the per-tenant endpoints (ADR 003, Consequences).
   */
  private static void requireBackupIdMatchesEveryTenantsMode(
      final List<PhysicalTenantBackupPort> targets, final @Nullable Long backupId) {
    if (backupId == null) {
      final var requiringAnId = physicalTenantIds(targets, port -> !port.backupIdGenerated());
      if (!requiringAnId.isEmpty()) {
        throw new ServiceException(
            ("A backupId is required because the physical tenants %s take manually triggered "
                    + "backups. Omit it only when every targeted physical tenant generates its own "
                    + "ids, which continuous backups and a backup or checkpoint schedule do.")
                .formatted(requiringAnId),
            Status.INVALID_ARGUMENT);
      }
      return;
    }

    final var generatingIds =
        physicalTenantIds(targets, PhysicalTenantBackupPort::backupIdGenerated);
    if (!generatingIds.isEmpty()) {
      throw new ServiceException(
          ("Cannot take a backup with an explicit backupId because the physical tenants %s generate "
                  + "their own ids, having continuous backups and/or a backup or checkpoint "
                  + "schedule enabled. Take a backup without specifying a backupId.")
              .formatted(generatingIds),
          Status.INVALID_ARGUMENT);
    }
    requirePositiveBackupId(backupId);
  }

  /**
   * Rejects an id the {@code BackupId} schema does not allow, so it never reaches a tenant. Without
   * this, {@code 0} and negative ids read back as a 404 — indistinguishable from an id that is
   * merely absent, when the request could never have been served at all.
   */
  private static void requirePositiveBackupId(final long backupId) {
    if (backupId <= 0) {
      throw new ServiceException(
          "A backupId must be > 0, but was %d".formatted(backupId), Status.INVALID_ARGUMENT);
    }
  }

  private static List<String> physicalTenantIds(
      final List<PhysicalTenantBackupPort> targets,
      final Predicate<PhysicalTenantBackupPort> matching) {
    return targets.stream()
        .filter(matching)
        .map(PhysicalTenantBackupPort::physicalTenantId)
        .toList();
  }

  /**
   * Enforces the whole shape the {@code BackupIdPrefix} schema publishes — digits followed by a
   * single wildcard — not merely that it ends in one. Backup ids are numbers, so a prefix like
   * {@code abc*} can never match anything: answering the documented 400 tells the caller its
   * request was malformed, where passing it to the store returns an empty list that reads as "no
   * backups".
   */
  private static String requireValidPrefix(final @Nullable String prefix) {
    if (prefix == null) {
      return BackupApi.WILDCARD;
    }
    if (!BACKUP_ID_PREFIX.matcher(prefix).matches()) {
      throw new ServiceException(
          "Expected a prefix of digits ending with a single '*', but got '%s'".formatted(prefix),
          Status.INVALID_ARGUMENT);
    }
    return prefix;
  }

  private static Page requireValidPage(final @Nullable Long before, final @Nullable Integer limit) {
    if (limit != null && (limit < 1 || limit > BackupApi.MAX_PAGE_SIZE)) {
      throw new ServiceException(
          "Expected a limit between 1 and %d, but got %d".formatted(BackupApi.MAX_PAGE_SIZE, limit),
          Status.INVALID_ARGUMENT);
    }
    if (before != null && before < 0) {
      throw new ServiceException(
          "Expected a backup id as before cursor, but got %d".formatted(before),
          Status.INVALID_ARGUMENT);
    }
    return new Page(
        before != null ? OptionalLong.of(before) : OptionalLong.empty(),
        limit != null ? OptionalInt.of(limit) : OptionalInt.empty());
  }

  private static CompletionStage<Long> take(
      final PhysicalTenantBackupPort target, final @Nullable Long backupId) {
    return backupId == null
        ? target.api().takeBackup(target.physicalTenantId())
        : target.api().takeBackup(target.physicalTenantId(), backupId);
  }

  private static CompletionStage<RuntimeBackupState> readRuntimeState(
      final PhysicalTenantBackupPort target) {
    final var checkpointState =
        target.api().getCheckpointState(target.physicalTenantId()).toCompletableFuture();
    final var ranges =
        target.api().getBackupRanges(target.physicalTenantId()).toCompletableFuture();
    return checkpointState.thenCombine(ranges, RuntimeBackupState::new);
  }

  private static CompletionStage<RuntimeBackupState> writeRuntimeState(
      final PhysicalTenantBackupPort target) {
    final var ranges = target.api().syncMetadata(target.physicalTenantId()).toCompletableFuture();
    final var checkpointState =
        target.api().getCheckpointState(target.physicalTenantId()).toCompletableFuture();
    return checkpointState.thenCombine(ranges, RuntimeBackupState::new);
  }

  /**
   * Starts one request per targeted physical tenant and waits for all of them, keeping the targets'
   * order. Unlike the read paths, this does not decide what a failure means — that is the caller's,
   * because triggering a backup and reading one answer a failed tenant differently.
   */
  private <T> CompletableFuture<List<Outcome<T>>> onEveryTenant(
      final List<PhysicalTenantBackupPort> targets,
      final Function<PhysicalTenantBackupPort, CompletionStage<T>> perTenant) {
    final var outcomes = targets.stream().map(target -> attempt(target, perTenant)).toList();
    return CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> outcomes.stream().map(CompletableFuture::join).toList());
  }

  /**
   * {@link BackupApi} validates the topology before it returns a future, so a tenant with too few
   * reachable partitions throws instead of failing its future. Catching that here keeps one broken
   * tenant from aborting the fan-out before the healthy tenants are even asked.
   */
  private static <T> CompletableFuture<Outcome<T>> attempt(
      final PhysicalTenantBackupPort target,
      final Function<PhysicalTenantBackupPort, CompletionStage<T>> perTenant) {
    try {
      return perTenant
          .apply(target)
          .toCompletableFuture()
          .handle((value, error) -> new Outcome<>(target.physicalTenantId(), value, error));
    } catch (final RuntimeException e) {
      return CompletableFuture.completedFuture(Outcome.failed(target.physicalTenantId(), e));
    }
  }

  private static ClusterRuntimeBackupTaken toTaken(
      final List<Outcome<Long>> outcomes, final @Nullable Long requestedBackupId) {
    final var perTenant =
        outcomes.stream().map(outcome -> toTenantOutcome(outcome, requestedBackupId)).toList();
    final var failureStatuses =
        outcomes.stream()
            .filter(Outcome::isFailure)
            .map(outcome -> outcome.cause().getStatus())
            .toList();
    return new ClusterRuntimeBackupTaken(
        perTenant,
        failureStatuses.isEmpty() ? null : PhysicalTenantFanOut.sharedStatus(failureStatuses));
  }

  /**
   * Classifies what one physical tenant did with the trigger.
   *
   * <p>A failure whose status says the broker may still have accepted the request — the connection
   * was cut mid-flight, or the gateway timed out waiting — is reported as {@link
   * TakeOutcome#UNKNOWN} rather than {@code FAILED}, and keeps the requested id. Calling it {@code
   * FAILED} would tell the operator no backup is running on that tenant, which is the one thing
   * this response exists not to get wrong: a backup left running under an id nobody was told about
   * is exactly the silent partial trigger ADR 003 D4 forbids.
   *
   * <p>The residual gap is a tenant that generates its own ids: the id is generated behind {@link
   * BackupApi}, so a call that never completes has none to report and the caller has to list that
   * tenant's backups to find it. Closing that needs the port to surface the id it attempted.
   */
  private static PhysicalTenantRuntimeBackupTaken toTenantOutcome(
      final Outcome<Long> outcome, final @Nullable Long requestedBackupId) {
    if (!outcome.isFailure()) {
      return new PhysicalTenantRuntimeBackupTaken(
          outcome.physicalTenantId(), TakeOutcome.TRIGGERED, outcome.requireValue(), null);
    }
    final var cause = outcome.cause();
    final var indeterminate =
        cause.getStatus() == Status.ABORTED || cause.getStatus() == Status.DEADLINE_EXCEEDED;
    return new PhysicalTenantRuntimeBackupTaken(
        outcome.physicalTenantId(),
        indeterminate ? TakeOutcome.UNKNOWN : TakeOutcome.FAILED,
        indeterminate ? requestedBackupId : null,
        cause.getMessage());
  }

  /**
   * Enforces the all-or-nothing rule for the operations that produce no value, whose successful
   * outcomes carry none: a {@code CompletionStage<Void>} completes with {@code null}.
   */
  private static @Nullable Void everyTenantSucceeded(final List<? extends Outcome<?>> outcomes) {
    PhysicalTenantFanOut.requireNoTenantFailed(outcomes);
    return null;
  }

  /**
   * Pairs each targeted physical tenant with its own result. {@link #onEveryTenant} keeps the
   * targets' order, which is what makes pairing by position sound.
   */
  private static <T, R> List<R> zip(
      final List<PhysicalTenantBackupPort> targets,
      final List<T> results,
      final BiFunction<String, T, R> pair) {
    return IntStream.range(0, targets.size())
        .mapToObj(i -> pair.apply(targets.get(i).physicalTenantId(), results.get(i)))
        .toList();
  }

  /**
   * Groups the tenants' backups by backup id, most recent id first, reporting every targeted tenant
   * under every id — including the tenants that hold nothing for it.
   *
   * <p>Listing only the holders would make the same {@link ClusterRuntimeBackup} mean two different
   * things: a backup half the cluster is missing would read {@code COMPLETED} in a listing and
   * {@code INCOMPLETE} when looked up directly. An operator scanning the listing for the backups
   * the cluster can actually be restored from has to be able to trust the state, so a tenant that
   * holds nothing for an id contributes {@link State#DOES_NOT_EXIST} here, exactly as it does on
   * the single-id read.
   */
  private static List<ClusterRuntimeBackup> groupByBackupId(
      final List<PhysicalTenantBackupPort> targets,
      final List<List<BackupStatus>> perTenant,
      final OptionalInt limit) {
    // ids below the bound wait for the next page: a tenant that filled its page may still hold them
    final var bound =
        PagedListing.safeBound(
            perTenant.stream()
                .map(
                    backups ->
                        backups.stream().map(BackupStatus::backupId).collect(Collectors.toSet()))
                .toList(),
            limit);
    final Map<Long, Map<String, BackupStatus>> holdersByBackupId =
        new TreeMap<>(Comparator.reverseOrder());
    zip(targets, perTenant, PhysicalTenantBackups::new)
        .forEach(
            tenant ->
                tenant.backups().stream()
                    .filter(backup -> backup.backupId() >= bound)
                    .forEach(
                        backup ->
                            holdersByBackupId
                                .computeIfAbsent(backup.backupId(), id -> new HashMap<>())
                                .put(tenant.physicalTenantId(), backup)));
    final var grouped =
        holdersByBackupId.entrySet().stream()
            .map(entry -> toClusterBackup(entry.getKey(), everyTenant(targets, entry)));
    return (limit.isPresent() ? grouped.limit(limit.getAsInt()) : grouped).toList();
  }

  /**
   * Reports each targeted tenant's backup for one id, filling in the tenants that hold none. The
   * filled-in entry carries no partition detail: a listing asks each tenant for the backups it has,
   * so there is nothing to report per partition for one it does not.
   */
  private static List<PhysicalTenantRuntimeBackup> everyTenant(
      final List<PhysicalTenantBackupPort> targets,
      final Map.Entry<Long, Map<String, BackupStatus>> holders) {
    return targets.stream()
        .map(
            target ->
                new PhysicalTenantRuntimeBackup(
                    target.physicalTenantId(),
                    holders
                        .getValue()
                        .getOrDefault(target.physicalTenantId(), absent(holders.getKey()))))
        .toList();
  }

  private static BackupStatus absent(final long backupId) {
    return new BackupStatus(backupId, State.DOES_NOT_EXIST, Optional.empty(), List.of());
  }

  private static ClusterRuntimeBackup toClusterBackup(
      final long backupId, final List<PhysicalTenantRuntimeBackup> physicalTenants) {
    final var state = fold(physicalTenants);
    return new ClusterRuntimeBackup(
        backupId,
        state,
        state == State.FAILED ? collectFailureReason(physicalTenants) : null,
        physicalTenants);
  }

  /**
   * Folds the physical tenants' states into the cluster-wide one, by the same rules {@code
   * BackupRequestHandler} folds a tenant's partitions into the tenant's state: a cluster-wide
   * backup relates to its tenants' backups exactly as a tenant's backup relates to its partitions',
   * so an operator reads one state code either way.
   *
   * <p>{@code INCOMPLETE} is checked ahead of those rules because only a tenant state can carry it
   * — a partition never does — and a tenant that is itself incomplete makes the whole set
   * incomplete.
   */
  private static State fold(final List<PhysicalTenantRuntimeBackup> physicalTenants) {
    if (physicalTenants.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected at least one physical tenant to fold a backup state from");
    }
    final var states =
        physicalTenants.stream()
            .map(tenant -> tenant.backup().status())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(State.class)));

    if (states.contains(State.FAILED)) {
      return State.FAILED;
    }
    if (states.contains(State.INCOMPLETE)) {
      return State.INCOMPLETE;
    }
    if ((states.contains(State.IN_PROGRESS) || states.contains(State.COMPLETED))
        && states.contains(State.DOES_NOT_EXIST)
        && !states.contains(State.DELETED)) {
      return State.INCOMPLETE;
    }
    if (states.contains(State.DELETED)) {
      return State.DELETED;
    }
    if (states.contains(State.IN_PROGRESS)) {
      return State.IN_PROGRESS;
    }
    if (states.contains(State.DOES_NOT_EXIST)) {
      return State.DOES_NOT_EXIST;
    }
    return State.COMPLETED;
  }

  private static String collectFailureReason(
      final List<PhysicalTenantRuntimeBackup> physicalTenants) {
    return physicalTenants.stream()
        .filter(tenant -> tenant.backup().status() == State.FAILED)
        .map(
            tenant ->
                "Backup on physical tenant '%s' failed due to %s. "
                    .formatted(
                        tenant.physicalTenantId(),
                        tenant.backup().failureReason().orElse("Unknown reason")))
        .collect(Collectors.joining());
  }

  private static ServiceException noTenantHolds(
      final long backupId, final List<PhysicalTenantBackupPort> targets) {
    return new ServiceException(
        "Expected to find a runtime backup with id '%d', but none of the physical tenants %s holds it"
            .formatted(backupId, physicalTenantIds(targets, port -> true)),
        Status.NOT_FOUND);
  }

  /** Turns a rejection raised before the fan-out starts into a failed future. */
  private static <T> CompletableFuture<T> validated(final Supplier<CompletableFuture<T>> request) {
    try {
      return request.get();
    } catch (final ServiceException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private record PhysicalTenantBackups(String physicalTenantId, List<BackupStatus> backups) {}

  /** The validated page of a listing request, empty parts meaning "all" and "from the newest". */
  private record Page(OptionalLong before, OptionalInt limit) {}

  /**
   * One physical tenant's runtime-backup port, plus the backup-id mode it is configured in — the
   * deployment-time choice that decides whether this tenant accepts an explicit backup id.
   */
  public record PhysicalTenantBackupPort(
      String physicalTenantId, BackupApi api, boolean backupIdGenerated) {}

  /**
   * What each targeted physical tenant did with one trigger request.
   *
   * @param failureStatus the status the request should answer with, or {@code null} when every
   *     targeted tenant was triggered
   */
  public record ClusterRuntimeBackupTaken(
      List<PhysicalTenantRuntimeBackupTaken> physicalTenants, @Nullable Status failureStatus) {}

  /**
   * @param backupId the id to monitor or delete this tenant's backup by — the one it is running
   *     under when {@code TRIGGERED}, or the requested one to check when {@code UNKNOWN}. Null when
   *     the tenant is known not to be running one, and when an {@code UNKNOWN} tenant generates its
   *     own ids and never reported the one it generated.
   * @param reason why the tenant did not report a triggered backup, null when it did
   */
  public record PhysicalTenantRuntimeBackupTaken(
      String physicalTenantId,
      TakeOutcome outcome,
      @Nullable Long backupId,
      @Nullable String reason) {}

  /** What one physical tenant did with a trigger request. */
  public enum TakeOutcome {
    /** The backup is running on this tenant. Not that it completed — poll its status for that. */
    TRIGGERED,
    /** This tenant is running no backup for this request, and nothing has to be cleaned up. */
    FAILED,
    /**
     * The broker may or may not have accepted the request: the connection was cut mid-flight, or
     * the gateway timed out waiting. Check this tenant's backups before retrying.
     */
    UNKNOWN
  }

  /** What each physical tenant reports for one backup id, plus the state folded over them. */
  public record ClusterRuntimeBackup(
      long backupId,
      State state,
      @Nullable String failureReason,
      List<PhysicalTenantRuntimeBackup> physicalTenants) {}

  public record PhysicalTenantRuntimeBackup(String physicalTenantId, BackupStatus backup) {}

  /** The checkpoint and backup state of each targeted physical tenant, aggregated over none. */
  public record ClusterRuntimeBackupStates(
      List<PhysicalTenantRuntimeBackupState> physicalTenants) {}

  public record PhysicalTenantRuntimeBackupState(
      String physicalTenantId, RuntimeBackupState state) {}
}
