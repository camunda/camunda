/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.BackupTenantInfo;
import io.camunda.management.backups.BackupType;
import io.camunda.management.backups.CheckpointState;
import io.camunda.management.backups.CheckpointType;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.management.backups.PartitionBackupState;
import io.camunda.management.backups.PartitionCheckpointState;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.backup.client.api.BackupAlreadyExistException;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.util.VisibleForTesting;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * The {@code backupRuntime} actuator. Every operation covers all physical tenants of the cluster,
 * and takes an optional {@code physicalTenant} query parameter that narrows it to one (ADR 003 D3).
 * A single-tenant cluster has exactly one tenant, {@code default}, so its behavior is unchanged.
 *
 * <p>A cluster-wide backup is a <em>set of independent per-tenant backups</em>, not an atomic
 * snapshot: each tenant has its own partition group, backup store and backup configuration. A
 * caller-supplied id is therefore used verbatim for every tenant — the stores are separate, so the
 * ids cannot collide — while generated ids are drawn per tenant and reported in {@code backupIds}.
 *
 * <p>{@code POST} operations declare their {@code physicalTenant} parameter on an overload that
 * also accepts a request body, because Spring Boot's actuator makes any write operation with a
 * parameter consume {@code application/json}. The bodyless overloads must stay parameterless so
 * that a plain {@code curl -XPOST} keeps working; they cover every physical tenant, which is what
 * an unscoped request means anyway.
 */
@Component
@WebEndpoint(id = "backupRuntime")
public final class BackupEndpoint {

  /**
   * Cross-tenant precedence for the aggregated state of one backup, mirroring the per-partition
   * rule documented on {@code BackupInfo}: any tenant failing fails the whole backup, and a backup
   * that only some tenants have is incomplete rather than complete. {@link State#DOES_NOT_EXIST}
   * maps to {@link StateCode#INCOMPLETE} because a backup missing from every tenant is answered
   * with a 404 before aggregation, so seeing it here means at least one other tenant does have it.
   */
  private static final List<State> STATE_PRECEDENCE =
      List.of(
          State.FAILED,
          State.INCOMPLETE,
          State.DOES_NOT_EXIST,
          State.IN_PROGRESS,
          State.DELETED,
          State.COMPLETED);

  private final SequencedMap<String, PhysicalTenantBackup> backups;

  @SuppressWarnings("unused") // used by Spring
  @Autowired
  public BackupEndpoint(final BrokerClient client, final PhysicalTenantResolver physicalTenants) {
    this(physicalTenantBackups(client, physicalTenants));
  }

  /**
   * Single-tenant wiring against a live broker, for tests that drive the endpoint end to end rather
   * than through a mocked {@link BackupApi}. Production wiring always comes from the physical
   * tenant configuration, so this deliberately covers the default tenant only.
   */
  @VisibleForTesting
  public BackupEndpoint(final BrokerClient client, final BackupCfg backupCfg) {
    this(
        new BackupRequestHandler(client, new CheckpointIdGenerator(backupCfg.getOffset())),
        backupCfg);
  }

  BackupEndpoint(final BackupApi api, final BackupCfg backupCfg) {
    this(
        new LinkedHashMap<>(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                new PhysicalTenantBackup(api, backupIdGenerated(backupCfg)))));
  }

  BackupEndpoint(final SequencedMap<String, PhysicalTenantBackup> backups) {
    this.backups = backups;
  }

  /**
   * Takes a backup of every physical tenant, or of {@code physicalTenant} alone. {@code backupId}
   * comes from the request body; omitting it asks every targeted tenant to generate one, which is
   * only allowed where continuous backups or a backup or checkpoint schedule is configured.
   */
  @WriteOperation
  public WebEndpointResponse<?> take(
      final @Nullable Long backupId, final @Nullable String physicalTenant) {
    final List<String> targets;
    try {
      targets = resolveTargets(physicalTenant);
    } catch (final UnknownPhysicalTenantException e) {
      return badRequest(e.getMessage());
    }

    if (backupId == null) {
      return takeGenerated(targets);
    }
    return takeExplicit(targets, backupId);
  }

  /**
   * Takes a backup with the given id across every physical tenant. Not an operation of its own —
   * Spring never selects it, since {@link #take(Long, String)} already covers {@code POST} with a
   * body — it only spares callers holding a plain id from passing a null tenant.
   */
  public WebEndpointResponse<?> take(final long backupId) {
    return take(backupId, null);
  }

  /**
   * Bodyless variant, kept parameterless so a request without a JSON content type still reaches an
   * operation. Always covers every physical tenant.
   */
  @WriteOperation
  public WebEndpointResponse<?> take() {
    return take(null, null);
  }

  /**
   * Force-writes backup metadata for every physical tenant, or for {@code physicalTenant} alone.
   * {@code path} is always {@code state/sync}; see {@link #write(String[])} for why this overload
   * exists.
   */
  @WriteOperation
  public WebEndpointResponse<?> write(
      @Selector(match = Match.ALL_REMAINING) final String[] path,
      final @Nullable String physicalTenant) {
    if (path.length != 2 || !BackupApi.STATE.equals(path[0]) || !BackupApi.SYNC.equals(path[1])) {
      return badRequest("Unknown write operation: " + String.join("/", path));
    }

    final List<String> targets;
    try {
      targets = resolveTargets(physicalTenant);
    } catch (final UnknownPhysicalTenantException e) {
      return badRequest(e.getMessage());
    }

    final var ranges = fanOut(targets, tenant -> api(tenant).api().syncMetadata(tenant));
    final var checkpointStates =
        fanOut(targets, tenant -> api(tenant).api().getCheckpointState(tenant));
    var failure = firstFailure(ranges);
    if (failure == null) {
      failure = firstFailure(checkpointStates);
    }
    if (failure != null) {
      return mapErrorResponse(failure);
    }
    return new WebEndpointResponse<>(toCheckpointState(targets, checkpointStates, ranges));
  }

  /**
   * Bodyless variant of {@link #write(String[], String)}, kept parameterless so {@code POST
   * /backupRuntime/state/sync} without a JSON content type still reaches an operation. Always
   * covers every physical tenant.
   */
  @WriteOperation
  public WebEndpointResponse<?> write(@Selector(match = Match.ALL_REMAINING) final String[] path) {
    return write(path, null);
  }

  @ReadOperation
  public WebEndpointResponse<?> listAll(final @Nullable String physicalTenant) {
    return query(BackupApi.WILDCARD, physicalTenant);
  }

  @ReadOperation
  public WebEndpointResponse<?> query(
      @Selector final String prefixOrId, final @Nullable String physicalTenant) {
    final List<String> targets;
    try {
      targets = resolveTargets(physicalTenant);
    } catch (final UnknownPhysicalTenantException e) {
      return badRequest(e.getMessage());
    }

    if (BackupApi.STATE.equals(prefixOrId)) {
      return state(targets);
    }
    if (prefixOrId.endsWith(BackupApi.WILDCARD)) {
      return listPrefix(targets, prefixOrId);
    }
    final long id;
    try {
      id = Long.parseLong(prefixOrId);
    } catch (final NumberFormatException e) {
      return badRequest(
          "Expected a backup ID or prefix ending with '*', but got '%s'.".formatted(prefixOrId));
    }
    return status(targets, id);
  }

  @DeleteOperation
  public WebEndpointResponse<?> delete(
      @Selector final String id, final @Nullable String physicalTenant) {
    final List<String> targets;
    try {
      targets = resolveTargets(physicalTenant);
    } catch (final UnknownPhysicalTenantException e) {
      return badRequest(e.getMessage());
    }

    if (BackupApi.STATE.equals(id)) {
      return deleteState(targets);
    }
    final long backupId;
    try {
      backupId = Long.parseLong(id);
    } catch (final NumberFormatException e) {
      return badRequest("Expected a backup ID or 'state', but got '%s'.".formatted(id));
    }
    return deleteBackup(targets, backupId);
  }

  private WebEndpointResponse<?> takeExplicit(final List<String> targets, final long backupId) {
    // an explicit id cannot be honored by a tenant that generates its own, and a cluster-wide
    // backup must mean the same thing in every tenant, so a single such tenant rejects the request
    final var generating = targets.stream().filter(tenant -> api(tenant).generatesIds()).toList();
    if (!generating.isEmpty()) {
      return badRequest(
          "Cannot take backup with predetermined backupId when continuous backups and/or"
              + " backup or checkpoint scheduler is enabled."
              + " Use POST actuator/backupRuntime without specifying a backupId."
              + describeTenants(" Affected physical tenant(s): %s.", generating));
    }
    if (backupId <= 0) {
      return incorrectBackupIdErrorResponse();
    }

    final var taken = fanOut(targets, tenant -> api(tenant).api().takeBackup(tenant, backupId));
    return takeResponse(targets, taken);
  }

  private WebEndpointResponse<?> takeGenerated(final List<String> targets) {
    final var explicit = targets.stream().filter(tenant -> !api(tenant).generatesIds()).toList();
    if (!explicit.isEmpty()) {
      return incorrectBackupIdErrorResponse();
    }

    final var taken = fanOut(targets, tenant -> api(tenant).api().takeBackup(tenant));
    return takeResponse(targets, taken);
  }

  /**
   * Reports the ids of the backups that were triggered. On a partial failure the status code is the
   * failure's, and the message still names every tenant whose backup <em>was</em> triggered so the
   * operator can monitor or delete them (ADR 003 D4).
   */
  private WebEndpointResponse<?> takeResponse(
      final List<String> targets, final Map<String, Outcome<Long>> taken) {
    final var backupIds = new LinkedHashMap<String, Long>();
    targets.forEach(
        tenant -> {
          final var outcome = taken.get(tenant);
          if (outcome.error() == null) {
            backupIds.put(tenant, outcome.value());
          }
        });

    final var failure = firstFailure(taken);
    if (failure != null) {
      final var response = mapErrorResponse(failure);
      if (backupIds.isEmpty()) {
        return response;
      }
      return new WebEndpointResponse<>(
          new Error()
              .message(
                  response.getBody().getMessage()
                      + describeTenants(
                          " Backups were still triggered for physical tenant(s): %s.",
                          backupIds.keySet())),
          response.getStatus());
    }

    // the default tenant's id keeps the meaning backupId always had; the others are in backupIds.
    // A request narrowed to another tenant reports that tenant's id, since that is the only backup
    // the caller asked for.
    final var reportedId =
        backupIds.containsKey(DEFAULT_PHYSICAL_TENANT_ID)
            ? backupIds.get(DEFAULT_PHYSICAL_TENANT_ID)
            : backupIds.values().iterator().next();
    return new WebEndpointResponse<>(
        new TakeBackupRuntimeResponse()
            .backupId(reportedId)
            .backupIds(backupIds)
            .message(
                "A backup with id %d has been scheduled. Use GET actuator/backups/%d to monitor the status."
                    .formatted(reportedId, reportedId)),
        202);
  }

  private WebEndpointResponse<?> status(final List<String> targets, final long id) {
    final var statuses = fanOut(targets, tenant -> api(tenant).api().getStatus(tenant, id));
    final var failure = firstFailure(statuses);
    if (failure != null) {
      return mapErrorResponse(failure);
    }

    final var perTenant = new ArrayList<TenantBackupStatus>();
    targets.forEach(
        tenant -> perTenant.add(new TenantBackupStatus(tenant, statuses.get(tenant).value())));
    if (perTenant.stream().allMatch(s -> s.status().status() == State.DOES_NOT_EXIST)) {
      return new WebEndpointResponse<>(
          new Error().message("Backup with id %d does not exist".formatted(id)),
          WebEndpointResponse.STATUS_NOT_FOUND);
    }
    return new WebEndpointResponse<>(aggregate(perTenant));
  }

  private WebEndpointResponse<?> listPrefix(final List<String> targets, final String prefix) {
    final var listings = fanOut(targets, tenant -> api(tenant).api().listBackups(tenant, prefix));
    final var failure = firstFailure(listings);
    if (failure != null) {
      return mapErrorResponse(failure);
    }

    // one entry per backup id, holding every tenant that has a backup with that id
    final var byBackupId = new LinkedHashMap<Long, List<TenantBackupStatus>>();
    targets.forEach(
        tenant ->
            listings
                .get(tenant)
                .value()
                .forEach(
                    status ->
                        byBackupId
                            .computeIfAbsent(status.backupId(), ignored -> new ArrayList<>())
                            .add(new TenantBackupStatus(tenant, status))));

    final var response =
        byBackupId.entrySet().stream()
            .sorted(Map.Entry.<Long, List<TenantBackupStatus>>comparingByKey().reversed())
            .map(entry -> aggregate(entry.getValue()))
            .toList();
    return new WebEndpointResponse<>(response);
  }

  private WebEndpointResponse<?> deleteBackup(final List<String> targets, final long id) {
    final var deletions = fanOut(targets, tenant -> api(tenant).api().deleteBackup(tenant, id));
    final var failure = firstFailure(deletions);
    if (failure != null) {
      return mapErrorResponse(failure);
    }
    return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
  }

  private WebEndpointResponse<?> deleteState(final List<String> targets) {
    final var deletions = fanOut(targets, tenant -> api(tenant).api().deleteRuntimeState(tenant));
    final var failure = firstFailure(deletions);
    if (failure != null) {
      return mapErrorResponse(failure);
    }
    return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
  }

  /**
   * Reports the checkpoint and backup state. Unlike the other reads this one reports what it can
   * rather than failing: a tenant whose state cannot be fetched contributes no entries, so an
   * operator inspecting a partly unavailable cluster still sees the tenants that answered.
   */
  private WebEndpointResponse<?> state(final List<String> targets) {
    final var checkpointStates =
        fanOut(targets, tenant -> api(tenant).api().getCheckpointState(tenant));
    final var ranges = fanOut(targets, tenant -> api(tenant).api().getBackupRanges(tenant));
    return new WebEndpointResponse<>(toCheckpointState(targets, checkpointStates, ranges));
  }

  private CheckpointState toCheckpointState(
      final List<String> targets,
      final Map<String, Outcome<CheckpointStateResponse>> checkpointStates,
      final Map<String, Outcome<BackupRangesResponse>> ranges) {
    final var response = new CheckpointState();
    final var partitionCheckpointStates = new ArrayList<PartitionCheckpointState>();
    final var partitionBackupStates = new ArrayList<PartitionBackupState>();
    final var partitionRanges = new ArrayList<PartitionBackupRange>();

    for (final var tenant : targets) {
      final var checkpointState = checkpointStates.get(tenant).value();
      partitionCheckpointStates.addAll(
          mapResponse(
              checkpointState,
              CheckpointStateResponse::getCheckpointStates,
              state -> toCheckpointState(tenant, state),
              Comparator.comparingInt(PartitionCheckpointState::getPartitionId)));
      partitionBackupStates.addAll(
          mapResponse(
              checkpointState,
              CheckpointStateResponse::getBackupStates,
              state -> toBackupState(tenant, state),
              Comparator.comparingInt(PartitionBackupState::getPartitionId)));
      partitionRanges.addAll(
          mapResponse(
              ranges.get(tenant).value(),
              BackupRangesResponse::getRanges,
              range -> toRange(tenant, range),
              Comparator.comparingInt(PartitionBackupRange::getPartitionId)));
    }

    response.setCheckpointStates(partitionCheckpointStates);
    response.setBackupStates(partitionBackupStates);
    response.setRanges(partitionRanges);
    return response;
  }

  private static <S, T, R> List<R> mapResponse(
      final @Nullable S source,
      final Function<S, ? extends Collection<T>> extractor,
      final Function<T, R> mapper,
      final Comparator<R> comparator) {
    if (source == null) {
      return List.of();
    }
    return extractor.apply(source).stream().map(mapper).sorted(comparator).toList();
  }

  private PartitionCheckpointState toCheckpointState(
      final String physicalTenantId, final CheckpointStateResponse.PartitionCheckpointState state) {
    final var response = new PartitionCheckpointState();
    response.setPartitionId(state.partitionId());
    response.setPhysicalTenantId(physicalTenantId);
    response.setCheckpointId(state.checkpointId());
    response.setCheckpointPosition(state.checkpointPosition());
    response.setCheckpointTimestamp(
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(state.checkpointTimestamp()), ZoneId.of("UTC")));
    response.setCheckpointType(toCheckpointType(state.checkpointType()));
    return response;
  }

  private PartitionBackupState toBackupState(
      final String physicalTenantId, final CheckpointStateResponse.PartitionCheckpointState state) {
    final var response = new PartitionBackupState();
    response.setPartitionId(state.partitionId());
    response.setPhysicalTenantId(physicalTenantId);
    response.setCheckpointId(state.checkpointId());
    response.setCheckpointPosition(state.checkpointPosition());
    response.setFirstLogPosition(state.firstLogPosition());
    response.setCheckpointTimestamp(
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(state.checkpointTimestamp()), ZoneId.of("UTC")));
    response.setCheckpointType(toBackupType(state.checkpointType()));
    return response;
  }

  private PartitionBackupRange toRange(
      final String physicalTenantId, final BackupRangesResponse.PartitionBackupRange range) {
    final var response = new PartitionBackupRange();
    response.setPartitionId(range.partitionId());
    response.setPhysicalTenantId(physicalTenantId);
    response.setStart(
        range.first() == null ? null : toBackupState(physicalTenantId, range.first()));
    response.setEnd(range.last() == null ? null : toBackupState(physicalTenantId, range.last()));
    return response;
  }

  private PartitionBackupState toBackupState(
      final String physicalTenantId, final BackupRangesResponse.CheckpointInfo info) {
    final var state = new PartitionBackupState();
    state.setPhysicalTenantId(physicalTenantId);
    state.setCheckpointId(info.checkpointId());
    state.setCheckpointPosition(info.checkpointPosition());
    state.setFirstLogPosition(info.firstLogPosition());
    state.setCheckpointType(toBackupType(info.checkpointType()));
    state.setCheckpointTimestamp(
        OffsetDateTime.ofInstant(info.checkpointTimestamp(), ZoneId.of("UTC")));
    return state;
  }

  private CheckpointType toCheckpointType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    return switch (checkpointType) {
      case SCHEDULED_BACKUP -> CheckpointType.SCHEDULED_BACKUP;
      case MANUAL_BACKUP -> CheckpointType.MANUAL_BACKUP;
      case MARKER -> CheckpointType.MARKER;
      case null -> null;
    };
  }

  private BackupType toBackupType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    return switch (checkpointType) {
      case MANUAL_BACKUP -> BackupType.MANUAL_BACKUP;
      case SCHEDULED_BACKUP -> BackupType.SCHEDULED_BACKUP;
      case MARKER -> null;
      case null -> null;
    };
  }

  /**
   * Folds one backup's per-tenant statuses into a single {@link BackupInfo}. The top-level fields
   * stay the aggregate view a caller that predates physical tenants reads, while {@code
   * physicalTenants} keeps each tenant's own state addressable.
   */
  private BackupInfo aggregate(final List<TenantBackupStatus> perTenant) {
    final var info =
        new BackupInfo()
            .backupId(perTenant.getFirst().status().backupId())
            .state(worstStateOf(perTenant));
    final var failureReasons =
        perTenant.stream()
            .map(tenant -> tenant.status().failureReason())
            .flatMap(Optional::stream)
            .filter(reason -> !reason.isEmpty())
            .toList();
    if (!failureReasons.isEmpty()) {
      info.setFailureReason(String.join("; ", failureReasons));
    }
    info.setDetails(perTenant.stream().flatMap(tenant -> partitions(tenant).stream()).toList());
    info.setPhysicalTenants(perTenant.stream().map(this::toTenantInfo).toList());
    return info;
  }

  private BackupTenantInfo toTenantInfo(final TenantBackupStatus tenant) {
    final var info =
        new BackupTenantInfo()
            .physicalTenantId(tenant.physicalTenantId())
            .state(getBackupStateCode(tenant.status().status()))
            .details(partitions(tenant));
    tenant.status().failureReason().ifPresent(info::setFailureReason);
    return info;
  }

  private List<PartitionBackupInfo> partitions(final TenantBackupStatus tenant) {
    return tenant.status().partitions().stream()
        .map(partition -> toPartitionBackupInfo(tenant.physicalTenantId(), partition))
        .sorted(Comparator.comparingInt(PartitionBackupInfo::getPartitionId))
        .toList();
  }

  private StateCode worstStateOf(final List<TenantBackupStatus> perTenant) {
    final var present = perTenant.stream().map(tenant -> tenant.status().status()).toList();
    for (final State candidate : STATE_PRECEDENCE) {
      if (present.contains(candidate)) {
        return candidate == State.DOES_NOT_EXIST
            ? StateCode.INCOMPLETE
            : getBackupStateCode(candidate);
      }
    }
    return StateCode.COMPLETED;
  }

  private PartitionBackupInfo toPartitionBackupInfo(
      final String physicalTenantId, final PartitionBackupStatus partitionStatus) {
    final var partitionBackupInfo =
        new PartitionBackupInfo()
            .partitionId(partitionStatus.partitionId())
            .physicalTenantId(physicalTenantId)
            .state(getPartitionBackupStateCode(partitionStatus.status()));
    partitionStatus.failureReason().ifPresent(partitionBackupInfo::setFailureReason);
    partitionStatus
        .createdAt()
        .ifPresent(
            time -> {
              final var i = Instant.parse(time);
              partitionBackupInfo.createdAt(OffsetDateTime.ofInstant(i, ZoneId.of("UTC")));
            });
    partitionStatus
        .lastUpdatedAt()
        .ifPresent(
            time -> {
              final var i = Instant.parse(time);
              partitionBackupInfo.lastUpdatedAt(OffsetDateTime.ofInstant(i, ZoneId.of("UTC")));
            });
    partitionStatus.brokerId().ifPresent(partitionBackupInfo::setBrokerId);
    partitionStatus.brokerVersion().ifPresent(partitionBackupInfo::setBrokerVersion);
    partitionStatus.snapshotId().ifPresent(partitionBackupInfo::setSnapshotId);
    partitionStatus.firstLogPosition().ifPresent(partitionBackupInfo::setFirstLogPosition);
    partitionStatus.checkpointPosition().ifPresent(partitionBackupInfo::setCheckpointPosition);
    return partitionBackupInfo;
  }

  private StateCode getBackupStateCode(final State state) {
    return switch (state) {
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
      case FAILED -> StateCode.FAILED;
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      case INCOMPLETE -> StateCode.INCOMPLETE;
      case DELETED -> StateCode.DELETED;
    };
  }

  private StateCode getPartitionBackupStateCode(final BackupStatusCode status) {
    return switch (status) {
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
      case FAILED -> StateCode.FAILED;
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      case DELETED -> StateCode.DELETED;
      default -> throw new IllegalStateException("Unknown BackupState %s".formatted(status));
    };
  }

  private WebEndpointResponse<Error> mapErrorResponse(final Throwable exception) {
    final int errorCode;
    final String message;
    if (exception instanceof CompletionException) {
      final var error = exception.getCause();
      if (error instanceof BackupAlreadyExistException) {
        errorCode = 409;
        message = error.getMessage();
      } else if (error instanceof IncompleteTopologyException) {
        errorCode = 502;
        message = error.getMessage();
      } else if (error instanceof TimeoutException || error instanceof ConnectTimeoutException) {
        errorCode = 504;
        message = "Request from gateway to broker timed out. " + error.getMessage();
      } else if (error instanceof ConnectException) {
        errorCode = 502;
        message = "Failed to send request from gateway to broker." + error.getMessage();
      } else if (error instanceof final BrokerErrorException brokerError) {
        final var rootError = brokerError.getError();
        errorCode =
            switch (rootError.getCode()) {
              case PARTITION_LEADER_MISMATCH -> 502;
              case RESOURCE_EXHAUSTED -> WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE;
              case UNSUPPORTED_MESSAGE -> WebEndpointResponse.STATUS_BAD_REQUEST;
              default -> WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
            };
        message = rootError.getMessage();
      } else if (error instanceof final BrokerRejectionException brokerRejectionException) {
        errorCode = 409; // Conflict with concurrent scaling operation
        message =
            "Cannot take backup while scaling is in progress. Please retry after scaling is completed.";
      } else {
        errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
        message = error.getMessage();
      }
    } else {
      errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
      message = exception.getMessage();
    }

    return new WebEndpointResponse<>(new Error().message(message), errorCode);
  }

  private WebEndpointResponse<?> incorrectBackupIdErrorResponse() {
    return badRequest("A backupId must be provided and it must be > 0");
  }

  private static WebEndpointResponse<Error> badRequest(final String message) {
    return new WebEndpointResponse<>(
        new Error().message(message), WebEndpointResponse.STATUS_BAD_REQUEST);
  }

  private List<String> resolveTargets(final @Nullable String physicalTenant) {
    return PhysicalTenantScope.resolve(physicalTenant, backups::keySet);
  }

  private PhysicalTenantBackup api(final String physicalTenantId) {
    return backups.get(physicalTenantId);
  }

  /**
   * Sends one request per physical tenant, starting every request before waiting on any so a
   * cluster-wide operation costs the same round trips as a single-tenant one, and keeping each
   * tenant's outcome so a partial failure can be reported as one.
   *
   * <p>A synchronous throw is turned into a failed future so both failure shapes reach {@link
   * #mapErrorResponse} as a {@link CompletionException}, which maps them identically.
   */
  private static <T> Map<String, Outcome<T>> fanOut(
      final List<String> targets, final Function<String, CompletionStage<T>> call) {
    final var started = new LinkedHashMap<String, CompletableFuture<T>>();
    for (final var tenant : targets) {
      try {
        started.put(tenant, call.apply(tenant).toCompletableFuture());
      } catch (final Exception e) {
        started.put(tenant, CompletableFuture.failedFuture(e));
      }
    }

    final var outcomes = new LinkedHashMap<String, Outcome<T>>();
    started.forEach(
        (tenant, future) -> {
          try {
            outcomes.put(tenant, new Outcome<>(future.join(), null));
          } catch (final Exception e) {
            outcomes.put(tenant, new Outcome<>(null, e));
          }
        });
    return outcomes;
  }

  private static @Nullable Throwable firstFailure(
      final Map<String, ? extends Outcome<?>> outcomes) {
    for (final var outcome : outcomes.values()) {
      if (outcome.error() != null) {
        return outcome.error();
      }
    }
    return null;
  }

  private static String describeTenants(final String format, final Collection<String> tenants) {
    // a single-tenant cluster has nothing to disambiguate, and naming "default" there would only
    // add noise to a message that has read the same way for releases
    return tenants.size() > 1 || !tenants.contains(DEFAULT_PHYSICAL_TENANT_ID)
        ? format.formatted(tenants.stream().sorted().toList())
        : "";
  }

  private static SequencedMap<String, PhysicalTenantBackup> physicalTenantBackups(
      final BrokerClient client, final PhysicalTenantResolver physicalTenants) {
    final var backups = new LinkedHashMap<String, PhysicalTenantBackup>();
    physicalTenants
        .getAll()
        .forEach(
            (physicalTenantId, config) ->
                backups.put(physicalTenantId, physicalTenantBackup(client, config)));
    return backups;
  }

  private static PhysicalTenantBackup physicalTenantBackup(
      final BrokerClient client, final Camunda physicalTenantConfig) {
    final var backup = physicalTenantConfig.getData().getPrimaryStorage().getBackup();
    final var generatesIds =
        backup.isContinuous()
            || !(Schedule.parseSchedule(backup.getSchedule()) instanceof NoneSchedule)
            || (backup.getCheckpointInterval() != null && !backup.getCheckpointInterval().isZero());
    return new PhysicalTenantBackup(
        new BackupRequestHandler(client, new CheckpointIdGenerator(backup.getOffset())),
        generatesIds);
  }

  private static boolean backupIdGenerated(final BackupCfg backupCfg) {
    return backupCfg.isContinuous()
        || !(backupCfg.getSchedule() instanceof NoneSchedule)
        || (backupCfg.getCheckpointInterval() != null
            && !backupCfg.getCheckpointInterval().isZero());
  }

  /**
   * One physical tenant's backup client, together with whether that tenant generates its own backup
   * ids. Backup configuration is per tenant, so tenants of one cluster may disagree on this.
   */
  record PhysicalTenantBackup(BackupApi api, boolean generatesIds) {}

  private record TenantBackupStatus(String physicalTenantId, BackupStatus status) {}

  private record Outcome<T>(@Nullable T value, @Nullable Throwable error) {}
}
