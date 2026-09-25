/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.github.resilience4j.core.IntervalFunction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes every physical tenant's secondary-storage schema independently: one task per tenant,
 * each retrying in the background until it succeeds, with no single tenant's failure aborting
 * startup. Storage-agnostic — the attempt, the terminal classification and the retry configuration
 * are supplied by the caller, so that Elasticsearch/OpenSearch and RDBMS can share this component.
 *
 * <p>{@link #awaitGate()} blocks until every tenant has <em>settled</em> — initialized or failed at
 * least once — and either one tenant is serviceable or none is still trying. Requiring a
 * serviceable tenant is what keeps a node started alongside its storage from opening its port a
 * second in, on the first connect timeout, and then serving 503s until the storage finishes
 * booting; requiring it only while some tenant can still make progress is what keeps the gate from
 * waiting on a condition nothing can satisfy.
 *
 * <p>The one state the gate refuses to open into is every tenant terminal: that is a diagnosis that
 * no wait will change the outcome, so {@link #awaitGate()} throws instead and startup aborts. The
 * other ways a tenant can stop trying are not diagnoses, and they release the gate as above.
 *
 * <p>Only a caller that serves an HTTP surface calls {@link #awaitGate()}; every other caller
 * {@link #start()}s the tasks and returns.
 *
 * <p>{@link #isInitialized(String)} is a one-way latch: it asserts that the schema described in the
 * source code was applied, never that the tenant's storage is currently reachable.
 *
 * <p>A tenant may also be <em>deferred</em>: its schema must not be touched at all for now, which
 * is not a failure and is not retried against a budget. A deferred tenant stops counting as still
 * trying, so it never holds the gate shut, and it stays uninitialized until the deferral lifts.
 */
@NullMarked
public final class PerTenantSchemaInitialization implements SchemaInitialization {

  private static final Logger LOG = LoggerFactory.getLogger(PerTenantSchemaInitialization.class);

  /**
   * Bounds the cause-chain walk of the terminal classification, so that a cyclic chain cannot spin
   * it forever.
   */
  private static final int MAX_CAUSE_DEPTH = 32;

  private final Consumer<String> attempt;
  private final Predicate<Throwable> terminal;
  private final Function<String, RetryConfiguration> retryConfig;
  private final DeferralCheck deferral;

  private final Map<String, TenantState> tenants;

  private final ReentrantLock gateLock = new ReentrantLock();
  private final Condition gateChanged = gateLock.newCondition();
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  // written by the thread that starts the tasks, read by whichever thread closes this
  private final List<Thread> workers = new CopyOnWriteArrayList<>();

  /**
   * @param tenantIds the physical tenants to initialize, one background task each
   * @param attempt applies one tenant's schema in a single attempt; throws to report failure
   * @param terminal decides whether one failure will not be repaired by retrying — the cause chain
   *     is walked for the caller, so this only classifies a single throwable. A tenant classified
   *     terminal stops trying, and if every tenant is classified that way the gate aborts startup
   *     rather than releasing, so the bar is "certainly not repairable without operator action".
   * @param retryConfig the backoff applied between a tenant's attempts
   * @see #PerTenantSchemaInitialization(Set, Consumer, Predicate, Function, Predicate) for the
   *     variant that can defer a tenant; this one never defers one.
   */
  public PerTenantSchemaInitialization(
      final Set<String> tenantIds,
      final Consumer<String> attempt,
      final Predicate<Throwable> terminal,
      final Function<String, RetryConfiguration> retryConfig) {
    this(tenantIds, attempt, terminal, retryConfig, tenantId -> false);
  }

  /**
   * @param deferred whether a tenant's schema must not be applied yet. Consulted before every
   *     attempt, so a deferral that lifts while the node runs is picked up on its own. A deferred
   *     tenant costs no attempt from its retry budget and produces no failure: the reason it is
   *     deferred is a state of the cluster, not of the storage, and classifying it as either
   *     success or failure would misreport it in both the logs and the gate.
   */
  public PerTenantSchemaInitialization(
      final Set<String> tenantIds,
      final Consumer<String> attempt,
      final Predicate<Throwable> terminal,
      final Function<String, RetryConfiguration> retryConfig,
      final Predicate<String> deferred) {
    this(
        tenantIds,
        attempt,
        terminal,
        retryConfig,
        DeferralCheck.of(tenantId -> deferred.test(tenantId) ? Deferral.DEFERRED : Deferral.NONE));
  }

  /**
   * Creates an initializer with a deferral check that can distinguish genuine recovery from pending
   * discovery. Pending discovery keeps the tenant trying and unsettled; genuine recovery releases
   * it from the gate as required by the per-tenant schema initialization ADR.
   *
   * @param tenantIds the physical tenants to initialize, one background task each
   * @param attempt applies one tenant's schema in a single attempt; throws to report failure
   * @param terminal decides whether one failure will not be repaired by retrying
   * @param retryConfig the backoff applied between a tenant's attempts
   * @param deferral returns the current deferral state for a tenant
   */
  public PerTenantSchemaInitialization(
      final Set<String> tenantIds,
      final Consumer<String> attempt,
      final Predicate<Throwable> terminal,
      final Function<String, RetryConfiguration> retryConfig,
      final DeferralCheck deferral) {
    this.attempt = attempt;
    this.terminal = terminal;
    this.retryConfig = retryConfig;
    this.deferral = deferral;

    // insertion-ordered so that tasks are started, and logged, in the order the tenants are
    // configured in
    final var states = new LinkedHashMap<String, TenantState>();
    tenantIds.forEach(tenantId -> states.put(tenantId, new TenantState()));
    tenants = Collections.unmodifiableMap(states);
  }

  /**
   * Starts one background task per physical tenant and returns as soon as they are running. A
   * storage failure degrades its own tenant and nothing else.
   */
  @Override
  public void start() {
    tenants.forEach(
        (tenantId, state) -> {
          // platform, not virtual. The virtual-thread scheduler's parallelism defaults to the
          // CPU count, so a handful of tenants first-touching a class at once can pin every
          // carrier (JEP-491 removed most pinning cases, but not class initialization) and
          // starve the whole scheduler, hanging the node (see #61405)
          final var worker =
              Thread.ofPlatform()
                  .daemon()
                  .name("schema-init-" + tenantId)
                  .unstarted(() -> initializeTenant(tenantId, state));
          // registered before it runs, so that a close() racing this loop still interrupts it
          workers.add(worker);
          try {
            worker.start();
          } catch (final Exception notStarted) {
            // A tenant whose task never runs would keep the gate shut forever, and a silent
            // permanent startup hang is a worse outcome than a degraded tenant.
            LOG.error(
                "Could not start the schema-initialization task of physical tenant '{}', so the"
                    + " tenant stays degraded until the node is restarted. Other physical tenants"
                    + " are unaffected.",
                tenantId,
                notStarted);
            abort(state, notStarted);
            stopTrying(state);
          }
        });
  }

  /**
   * Blocks until the gate opens: every tenant has settled and either one is serviceable or none is
   * still trying. Returns immediately once that already holds, and for a node with no tenants at
   * all.
   *
   * <p>The wait is interruptible so that a shutdown signal arriving mid-initialization releases it.
   * A shutdown-triggered interrupt is deliberately not re-raised: the Spring context refresh has to
   * be allowed to finish for the broker to shut down gracefully.
   *
   * @throws EveryTenantTerminallyFailedException if the gate opened with no serviceable tenant and
   *     every tenant terminal, which aborts the caller's startup
   */
  @Override
  public void awaitGate() {
    gateLock.lock();
    try {
      while (!isGateOpen()) {
        gateChanged.await();
      }
      failIfEveryTenantFailedTerminally();
    } catch (final InterruptedException e) {
      LOG.debug(
          "Interrupted while waiting for a serviceable physical tenant. Shutdown signal is"
              + " caught={}",
          shutdown.get(),
          e);
      if (!shutdown.get()) {
        Thread.currentThread().interrupt();
      }
    } finally {
      gateLock.unlock();
    }
  }

  /** Whether the physical tenant's schema has been applied. An unknown tenant is never ready. */
  @Override
  public boolean isInitialized(final String physicalTenantId) {
    final TenantState state = tenants.get(physicalTenantId);
    return state != null && state.ready.get();
  }

  /**
   * Where the physical tenant's initialization stands. Takes the lock the tasks write under, which
   * is fine for an operator's health check but not for anything consulted per request — that is
   * what {@link #isInitialized(String)} is for.
   *
   * @throws IllegalArgumentException for a tenant this node does not initialize
   */
  @Override
  public SchemaInitializationStatus status(final String physicalTenantId) {
    final TenantState state = tenants.get(physicalTenantId);
    if (state == null) {
      throw new IllegalArgumentException(
          "Physical tenant '" + physicalTenantId + "' is not initialized by this node");
    }
    gateLock.lock();
    try {
      return new SchemaInitializationStatus(state.stage, state.failedAttempts, state.lastFailure);
    } finally {
      gateLock.unlock();
    }
  }

  /** Stops all retrying and opens the gate; idempotent. */
  @Override
  public void close() {
    if (!shutdown.compareAndSet(false, true)) {
      return;
    }
    workers.forEach(Thread::interrupt);
    // Open the gate here rather than leaving it to the interrupted tasks: a task blocked in a
    // storage read may not observe the interrupt before its client's socket timeout, and shutdown
    // must not wait that long.
    signalGateChanged();
  }

  /** Must be called with {@link #gateLock} held. */
  private boolean isGateOpen() {
    if (shutdown.get()) {
      return true;
    }
    boolean anyReady = false;
    boolean noneTrying = true;
    for (final TenantState state : tenants.values()) {
      if (!state.settled) {
        return false;
      }
      anyReady |= state.ready.get();
      noneTrying &= !state.trying;
    }
    return anyReady || noneTrying;
  }

  /**
   * Aborts startup for the one state releasing cannot help: nothing serviceable, and every tenant
   * stopped by a failure the classification called unrepairable. Any other reason for stopping — an
   * exhausted retry budget, a task that could not be run — leaves the gate released and the node up
   * but unable to serve, which is where an operator can still reach it.
   *
   * <p>Must be called with {@link #gateLock} held, and only once {@link #isGateOpen()} holds.
   */
  private void failIfEveryTenantFailedTerminally() {
    if (shutdown.get() || tenants.isEmpty()) {
      return;
    }
    final var terminalFailures = new LinkedHashMap<String, Throwable>();
    for (final var tenant : tenants.entrySet()) {
      final TenantState state = tenant.getValue();
      final Throwable failure = state.lastFailure;
      if (state.ready.get() || state.stage != State.FAILED || failure == null) {
        return;
      }
      terminalFailures.put(tenant.getKey(), failure);
    }
    throw EveryTenantTerminallyFailedException.of(terminalFailures);
  }

  private void initializeTenant(final String physicalTenantId, final TenantState state) {
    try {
      // read inside the try: everything that can throw — an unusable retry configuration included
      // — has to be covered by the finally below, or this tenant would keep the gate shut forever
      final RetryConfiguration retry = retryConfig.apply(physicalTenantId);
      // Floored at one attempt so that the give-up log below can only ever report a count the
      // tenant actually made. The bound is read after a failure, never before the first attempt, so
      // a configured 0 or a negative value costs the tenant no attempt either way — which is also
      // why this is floored here rather than in RetryConfiguration, where a non-positive value is
      // load-bearing for other consumers (PingHubRunner rejects it as invalid configuration).
      final int maxAttempts = Math.max(1, retry.getMaxRetries());
      final var backoff =
          IntervalFunction.ofExponentialRandomBackoff(
              retry.getMinRetryDelay(), retry.getRetryDelayMultiplier(), retry.getMaxRetryDelay());
      final long deferralPollMillis = Math.max(1L, retry.getMinRetryDelay().toMillis());

      int attemptNumber = 1;
      int consecutiveDeferrals = 0;
      boolean recoveryDeferred = false;
      while (!shutdown.get()) {
        switch (deferral.check(physicalTenantId)) {
          case DEFERRED -> {
            consecutiveDeferrals++;
            logDeferred(physicalTenantId, consecutiveDeferrals, deferralPollMillis);
            // Stops this tenant counting as still trying, so a node whose every tenant is deferred
            // still opens its gate and comes up. That is what keeps the operator able to reach the
            // node that has to be told the deferral is over.
            if (!recoveryDeferred) {
              deferForRecovery(state);
              recoveryDeferred = true;
            }
            if (!sleep(deferralPollMillis)) {
              return;
            }
            continue;
          }
          case PENDING -> {
            consecutiveDeferrals++;
            logDiscoveryPending(physicalTenantId, consecutiveDeferrals, deferralPollMillis);
            // Pending discovery is not the ADR's genuine-recovery case. Keep the tenant unsettled
            // and trying, otherwise the gate can open before this tenant has ever been examined.
            if (recoveryDeferred) {
              startTrying(state);
              recoveryDeferred = false;
            }
            if (!sleep(deferralPollMillis)) {
              return;
            }
            continue;
          }
          case NONE -> {
            consecutiveDeferrals = 0;
            if (recoveryDeferred) {
              startTrying(state);
              recoveryDeferred = false;
            }
          }
          default -> throw new IllegalStateException("Unknown schema initialization deferral");
        }

        final long retryDelayMillis;
        try {
          attempt.accept(physicalTenantId);
          markReady(state);
          logInitialized(physicalTenantId, attemptNumber);
          return;
        } catch (final Exception failure) {
          // unconditionally, before anything that decides what to do with the failure: the tenant
          // has produced an outcome, and that is all settling asserts
          settle(state);
          if (shutdown.get()) {
            LOG.debug(
                "Schema initialization for physical tenant '{}' failed during shutdown.",
                physicalTenantId,
                failure);
            return;
          }
          if (isTerminal(failure)) {
            LOG.error(
                "Schema initialization for physical tenant '{}' failed with a cause that retrying"
                    + " cannot repair, so it will not be retried. This tenant stays degraded, and"
                    + " its requests keep being rejected, until the cause is fixed and the node is"
                    + " restarted. If no other tenant can be served either, startup aborts.",
                physicalTenantId,
                failure);
            recordTerminal(state, failure);
            return;
          }
          if (attemptNumber >= maxAttempts) {
            recordFailure(state, failure, State.GAVE_UP);
            LOG.error(
                "Schema initialization for physical tenant '{}' failed on all {} configured"
                    + " attempts and will not be retried further. This tenant stays degraded, and"
                    + " its requests keep being rejected, until the node is restarted. Raise the"
                    + " retry max-retries to keep retrying.",
                physicalTenantId,
                maxAttempts,
                failure);
            return;
          }
          retryDelayMillis = backoff.apply(attemptNumber);
          recordFailure(state, failure, State.RETRYING);
          LOG.warn(
              "Schema initialization for physical tenant '{}' failed on attempt {}, retrying in"
                  + " {}ms. This tenant stays degraded meanwhile.",
              physicalTenantId,
              attemptNumber,
              retryDelayMillis,
              failure);
        }

        attemptNumber++;
        if (!sleep(retryDelayMillis)) {
          return;
        }
      }
    } catch (final Exception unexpected) {
      LOG.error(
          "Schema initialization for physical tenant '{}' could not be run at all, so the tenant"
              + " stays degraded until the node is restarted. Other physical tenants are"
              + " unaffected.",
          physicalTenantId,
          unexpected);
      abort(state, unexpected);
    } finally {
      // The one guarantee the gate rests on: however this task ends — success, terminal failure,
      // an exhausted retry budget, shutdown, or an Error this class never sees — it stops counting
      // as still trying. Miss this on any path and a node with no serviceable tenant hangs at the
      // gate forever, with nothing in the logs to say why.
      stopTrying(state);
    }
  }

  /**
   * Records a tenant's first outcome, whatever that outcome was.
   *
   * <p>Settling on its own never opens the gate onto an unserviceable node, which is why a failing
   * tenant may settle before its failure has been classified: settling leaves {@code trying} set,
   * so {@link #isGateOpen()} can only be satisfied by some <em>other</em> tenant being ready — and
   * a ready tenant is precisely the case {@link #failIfEveryTenantFailedTerminally()} declines to
   * abort. The order that does matter is {@link #recordTerminal} before {@link #stopTrying}.
   */
  private void settle(final TenantState state) {
    gateLock.lock();
    try {
      if (!state.settled) {
        state.settled = true;
        gateChanged.signalAll();
      }
    } finally {
      gateLock.unlock();
    }
  }

  /**
   * Records the failure that stopped a tenant for good, for the one cause the gate treats
   * differently from the rest.
   *
   * <p>Called before {@link #stopTrying}, never after, because stopping is what can open the gate:
   * the other order lets a waiter wake on the last tenant stopping and read a failure that has not
   * been written yet, and release into the state this exists to abort.
   */
  private void recordTerminal(final TenantState state, final Throwable failure) {
    recordFailure(state, failure, State.FAILED);
  }

  /** Records a failed attempt, and what the tenant does next because of it. */
  private void recordFailure(final TenantState state, final Throwable failure, final State next) {
    gateLock.lock();
    try {
      state.failedAttempts++;
      state.lastFailure = failure;
      state.stage = next;
    } finally {
      gateLock.unlock();
    }
  }

  /** Records why a task ended outside any attempt; the finally that stops it follows. */
  private void abort(final TenantState state, final Throwable cause) {
    gateLock.lock();
    try {
      state.lastFailure = cause;
      state.stage = State.ABORTED;
    } finally {
      gateLock.unlock();
    }
  }

  private void markReady(final TenantState state) {
    gateLock.lock();
    try {
      state.ready.set(true);
      state.settled = true;
      state.stage = State.INITIALIZED;
      // nothing reports the failures of a serviceable tenant, so none is kept for its lifetime
      state.lastFailure = null;
      gateChanged.signalAll();
    } finally {
      gateLock.unlock();
    }
  }

  /**
   * Records that a tenant's task has stopped for good. A task that stopped without ever producing
   * an outcome — it could not be set up at all, or shutdown won the race against its first attempt
   * — counts as settled too: it has stopped contributing either way, and leaving it unsettled would
   * hold the gate shut on a condition nothing can satisfy.
   *
   * <p>A task that ends without having recorded why — an {@link Error} passes every catch — is
   * reported as aborted, not as still initializing, retrying or recovering, which nothing is left
   * running to do. Shutdown is the exception: a node going down has no one left to report to.
   */
  private void stopTrying(final TenantState state) {
    gateLock.lock();
    try {
      state.trying = false;
      state.settled = true;
      if (!shutdown.get() && !hasStopped(state.stage)) {
        state.stage = State.ABORTED;
        // what ended the task was not recorded, so an earlier attempt's failure must not read as it
        state.lastFailure = null;
      }
      gateChanged.signalAll();
    } finally {
      gateLock.unlock();
    }
  }

  /**
   * Stops a tenant counting as still trying while it is in recovery mode, as {@link #stopTrying}
   * does, but without ending its task: it is picked up again by {@link #startTrying}.
   */
  private void deferForRecovery(final TenantState state) {
    gateLock.lock();
    try {
      state.trying = false;
      state.settled = true;
      state.stage = State.RECOVERING;
      gateChanged.signalAll();
    } finally {
      gateLock.unlock();
    }
  }

  private static boolean hasStopped(final State stage) {
    return switch (stage) {
      case INITIALIZED, FAILED, GAVE_UP, ABORTED -> true;
      case INITIALIZING, RETRYING, RECOVERING -> false;
    };
  }

  /** Resumes a tenant that {@link #deferForRecovery} held back. */
  private void startTrying(final TenantState state) {
    gateLock.lock();
    try {
      state.stage = state.failedAttempts > 0 ? State.RETRYING : State.INITIALIZING;
      if (!state.trying) {
        state.trying = true;
        gateChanged.signalAll();
      }
    } finally {
      gateLock.unlock();
    }
  }

  private void signalGateChanged() {
    gateLock.lock();
    try {
      gateChanged.signalAll();
    } finally {
      gateLock.unlock();
    }
  }

  private void logDeferred(
      final String physicalTenantId,
      final int consecutiveDeferrals,
      final long deferralPollMillis) {
    if (consecutiveDeferrals == 1) {
      LOG.info(
          "Not initializing the schema of physical tenant '{}' yet: the tenant is recovering;"
              + " checking again in {}ms.",
          physicalTenantId,
          deferralPollMillis);
    } else {
      LOG.debug(
          "Schema initialization for physical tenant '{}' is still deferred because the tenant is"
              + " recovering, after {} checks; checking again in {}ms.",
          physicalTenantId,
          consecutiveDeferrals,
          deferralPollMillis);
    }
  }

  private void logDiscoveryPending(
      final String physicalTenantId,
      final int consecutiveDeferrals,
      final long deferralPollMillis) {
    if (consecutiveDeferrals == 1) {
      LOG.info(
          "Not initializing the schema of physical tenant '{}' yet: recovery status is still"
              + " being discovered; checking again in {}ms.",
          physicalTenantId,
          deferralPollMillis);
    } else {
      LOG.debug(
          "Schema initialization for physical tenant '{}' is still waiting for recovery status"
              + " after {} checks; checking again in {}ms.",
          physicalTenantId,
          consecutiveDeferrals,
          deferralPollMillis);
    }
  }

  private void logInitialized(final String physicalTenantId, final int attemptNumber) {
    if (attemptNumber == 1) {
      LOG.info("Schema for physical tenant '{}' is initialized.", physicalTenantId);
    } else {
      LOG.info(
          "Schema for physical tenant '{}' is initialized after {} attempts, and the tenant is no"
              + " longer degraded.",
          physicalTenantId,
          attemptNumber);
    }
  }

  /** Returns false when the wait was cut short and this tenant's task should stop. */
  private boolean sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return !shutdown.get();
  }

  private boolean isTerminal(final Throwable failure) {
    Throwable cause = failure;
    for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (terminal.test(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /** Supplies one consistent deferral decision to the tenant loop. */
  public static final class DeferralCheck {
    private final Function<String, Deferral> check;

    private DeferralCheck(final Function<String, Deferral> check) {
      this.check = check;
    }

    public static DeferralCheck of(final Function<String, Deferral> check) {
      return new DeferralCheck(check);
    }

    private Deferral check(final String physicalTenantId) {
      return check.apply(physicalTenantId);
    }
  }

  /**
   * One tenant's contribution to the gate, and what {@link #status(String)} reports of it. Every
   * field but {@code ready} is written and read only under {@link #gateLock}; {@code ready} is also
   * written under it, but is read without the lock by {@link #isInitialized(String)}, which every
   * rejected request consults and which must not serialize on a lock shared with every other
   * tenant.
   *
   * <p>{@code stage} is {@link State#FAILED} for a terminal classification and for no other way of
   * stopping, which is the distinction the gate's abort rests on — not {@code trying}, which every
   * way of stopping clears alike.
   */
  private static final class TenantState {
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private boolean settled;
    private boolean trying = true;
    private State stage = State.INITIALIZING;
    private int failedAttempts;
    private @Nullable Throwable lastFailure;
  }

  public enum Deferral {
    /** The tenant is not recovering and can be initialized. */
    NONE,
    /** The tenant is genuinely recovering and must be left untouched. */
    DEFERRED,
    /** Discovery has not resolved whether the tenant is recovering yet. */
    PENDING
  }
}
