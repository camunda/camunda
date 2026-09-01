/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/**
 * Meters for resolving secret references against a secret store.
 *
 * <p>No meter here is tagged by secret name: the cardinality is unbounded and secret names are
 * customer data.
 */
@SuppressWarnings("NullableProblems")
public enum SecretResolutionMetricsDoc implements ExtendedMeterDocumentation {
  /** Latency of resolving one batch of secret references from a secret store */
  RESOLUTION_DURATION {
    @Override
    public String getDescription() {
      return "Latency of one batch resolution call against a secret store, covering the call "
          + "itself and not the follow-up commands the engine writes for its results. Split by how "
          + "the call ended, so a store timing out does not distort the latency of the calls that "
          + "came back.";
    }

    @Override
    public String getName() {
      return "camunda.secret.resolution.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "seconds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretResolutionKeyNames.STORE, SecretResolutionKeyNames.RESULT};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }

    @Override
    public Duration[] getTimerSLOs() {
      // a secret store is typically remote (AWS Secrets Manager, GCP Secret Manager), so the
      // interesting range runs from a local file read up to a request timing out — further than
      // MicrometerUtil.defaultPrometheusBuckets(), which stops at 10s
      return new Duration[] {
        Duration.ofMillis(10),
        Duration.ofMillis(100),
        Duration.ofMillis(500),
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofSeconds(60)
      };
    }
  },

  /** Outcomes the engine produced while resolving secret references */
  RESOLUTION_OUTCOME {
    @Override
    public String getDescription() {
      return "Number of secret reference resolutions that produced an outcome, per store. Counts "
          + "references throughout: every result value is terminal for the reference it counts, and "
          + "is only counted once the engine has written the follow-up command for it, so the "
          + "values can be summed or divided by one another. A reference whose store is unavailable "
          + "but still has retry attempts left is not counted at all, because it has not reached a "
          + "terminal outcome yet. A value can slightly over-count: a reference stops being pending "
          + "only once the follow-up command has been processed, so a cycle running before that "
          + "resolves it a second time and counts it again, even though the duplicate command is "
          + "then rejected. Read the counter as a rate signal per outcome rather than as an exact "
          + "number of references.";
    }

    @Override
    public String getName() {
      return "camunda.secret.resolution.outcome";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretResolutionKeyNames.STORE, SecretResolutionKeyNames.RESULT};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Resolution cycles a store failed unexpectedly in */
  RESOLUTION_CYCLE_ERROR {
    @Override
    public String getDescription() {
      return "Number of resolution cycles in which a store failed in a way the engine does not "
          + "model — an unexpected exception rather than a per-secret failure or an unreachable "
          + "store — per store. Always a bug, either in the store implementation or in the engine. "
          + "Counts cycles rather than references, and is a separate meter from "
          + "camunda.secret.resolution.outcome for that reason: the references such a cycle leaves "
          + "pending are retried, so counting them would scale the series with the pending backlog "
          + "and with how often the cycle runs, and neither is a quantity anyone can alert on. Does "
          + "not cover an Error thrown by a store: the engine recovers from a RuntimeException "
          + "only, and an Error instead fails the resolution task and shows up on "
          + "camunda.secret.resolution.duration with result=ERROR.";
    }

    @Override
    public String getName() {
      return "camunda.secret.resolution.cycle.error";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      // deliberately no `result`: the meter has a single meaning, so there is no value domain to
      // aggregate across and no way to add a cycle count to a reference count by accident
      return new KeyName[] {SecretResolutionKeyNames.STORE};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The delay a resolution cycle chose for the next one, tagged by why */
  CYCLE_DELAY {
    @Override
    public String getDescription() {
      return "The delay a resolution cycle chose for the next one, tagged by why: DRAINING when "
          + "more pending refs remained than the batch cap allowed this cycle to take (always "
          + "zero), WAKE when this cycle resolved something or was woken, IDLE_BACKOFF when it did "
          + "neither and no store is in retry cooldown, and RETRY_COOLDOWN when a store's cooldown "
          + "deadline set the delay instead. IDLE_BACKOFF is the one to watch: it grows "
          + "geometrically on consecutive misses (see SecretResolutionScheduler#nextIdleBackoff), "
          + "and its distribution says directly whether that ladder is behaving as intended, rather "
          + "than leaving it to be inferred from the cycle rate alone.";
    }

    @Override
    public String getName() {
      return "camunda.secret.resolution.cycle.delay";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "seconds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretResolutionKeyNames.RESULT};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }

    @Override
    public Duration[] getTimerSLOs() {
      // the domain of interest here starts well below MicrometerUtil.defaultPrometheusBuckets()'s
      // lowest bucket (5ms) and stops at schedulingInterval's default (5s) rather than running out
      // to the remote-call timeouts RESOLUTION_DURATION's buckets are built for
      return new Duration[] {
        Duration.ofMillis(10),
        Duration.ofMillis(50),
        Duration.ofMillis(100),
        Duration.ofMillis(250),
        Duration.ofMillis(500),
        Duration.ofSeconds(1),
        Duration.ofMillis(2500),
        Duration.ofSeconds(5)
      };
    }
  };

  /**
   * The value domain of the {@link SecretResolutionKeyNames#RESULT} tag on {@link #CYCLE_DELAY}.
   */
  public enum SecretResolutionCycleDelayReason {
    /** More pending refs remained than the batch cap allowed this cycle to take */
    DRAINING,
    /** This cycle resolved something, or a reference was requested since the last cycle ran */
    WAKE,
    /** Neither of the above, and no store is in retry cooldown */
    IDLE_BACKOFF,
    /** Neither of the above, and a store's retry cooldown deadline set the delay instead */
    RETRY_COOLDOWN
  }

  @SuppressWarnings("NullableProblems")
  public enum SecretResolutionKeyNames implements KeyName {
    /**
     * The ID of the secret store the references belong to, or {@link #NO_STORE} for a reference
     * that carries no store ID. The {@code camunda.secrets.<name>} syntax has no store dimension
     * yet (<a href="https://github.com/camunda/camunda/issues/56563">#56563</a>), so every
     * reference names the default store and that is the value this tag mostly carries.
     */
    STORE("store"),
    /**
     * What the measured operation resulted in: a {@link SecretResolutionOutcome} on {@link
     * SecretResolutionMetricsDoc#RESOLUTION_OUTCOME}, the coarser {@link
     * SecretResolutionCallResult} on {@link SecretResolutionMetricsDoc#RESOLUTION_DURATION}, which
     * measures a whole batch call and so cannot attribute a per-reference outcome to it, and (on
     * {@link SecretResolutionMetricsDoc#CYCLE_DELAY}, not a result at all, but the same tag reused
     * for it) a {@link SecretResolutionCycleDelayReason} naming why the cycle chose its delay
     * rather than what it resolved.
     */
    RESULT("result");

    /**
     * The {@link #STORE} value used when a secret reference carries no store ID.
     *
     * <p>Deliberately not spellable as a store ID: those are property-path segments under {@code
     * camunda.secrets.stores.<type>.<id>} (e.g. {@code main}, {@code store-a}, {@code aws-main}),
     * so a bare word like {@code none} would silently merge a configured store's series with the
     * no-store one. The brackets keep the two apart.
     */
    public static final String NO_STORE = "<none>";

    private final String key;

    SecretResolutionKeyNames(final String key) {
      this.key = key;
    }

    @Override
    public String asString() {
      return key;
    }
  }

  /**
   * The value domain of the {@link SecretResolutionKeyNames#RESULT} tag on {@link
   * #RESOLUTION_OUTCOME}.
   *
   * <p>The four per-secret failure values mirror {@link SecretErrorCode}. They are worth carrying
   * here because the engine collapses all of them to a single {@code ResolutionState.NOT_FOUND}
   * when it writes to the stream, so this counter is the only place the distinction between a
   * missing secret and a denied one survives.
   *
   * <p>Every value counts one secret reference that reached a terminal outcome. Nothing measured
   * per cycle belongs here: a counter whose values carry two different units cannot be summed or
   * divided across the tag, which is what {@link #RESOLUTION_CYCLE_ERROR} is a separate meter for.
   */
  public enum SecretResolutionOutcome {
    /** The store returned a value for the reference */
    RESOLVED,
    /** The store does not hold the reference */
    NOT_FOUND,
    /** The store refused to read the reference */
    ACCESS_DENIED,
    /** The reference is not valid for the store */
    INVALID_REF,
    /** The store holds the reference but could not read a value from it */
    UNREADABLE,
    /**
     * The store could not serve the reference at all: either it is not configured, or it could not
     * be reached and no retry attempt is left. Mirrors {@code ResolutionState.STORE_UNAVAILABLE},
     * which the engine writes for both cases.
     */
    STORE_UNAVAILABLE;

    /**
     * Maps a store's typed error code onto the tag value.
     *
     * <p>Exhaustive and without a {@code default} branch on purpose: {@link SecretErrorCode}
     * documents that every caller maps new codes in the same change, and this is what makes the
     * compiler enforce that here instead of silently bucketing a new code under an existing tag
     * value.
     */
    public static SecretResolutionOutcome from(final SecretErrorCode code) {
      return switch (code) {
        case NOT_FOUND -> NOT_FOUND;
        case ACCESS_DENIED -> ACCESS_DENIED;
        case INVALID_REF -> INVALID_REF;
        case UNREADABLE -> UNREADABLE;
      };
    }
  }

  /**
   * The value domain of the {@link SecretResolutionKeyNames#RESULT} tag on {@link
   * #RESOLUTION_DURATION}.
   *
   * <p>A batch call resolves many references at once and each of them can end differently, so the
   * timer can only say how the call itself ended. That distinction is what keeps a store timing out
   * after 60s out of the same histogram as the calls that returned.
   *
   * <p>{@link #STORE_UNAVAILABLE} is the one value shared with {@link SecretResolutionOutcome}, and
   * means the same failure on both, so that {@code result} value reads identically wherever it
   * appears. The other two are exclusive to this domain: a returning batch call has no single
   * per-reference outcome to report, and a call that threw leaves its references pending rather
   * than resolving any of them.
   */
  public enum SecretResolutionCallResult {
    /** The store returned, whatever the per-reference results were */
    RETURNED,
    /**
     * The store could not be reached. Unlike {@link SecretResolutionOutcome#STORE_UNAVAILABLE} this
     * says nothing about retries: it covers every unreachable call, not only the last one.
     */
    STORE_UNAVAILABLE,
    /**
     * The store threw something the engine does not model. Always a bug. Covers an {@code Error}
     * too, which the engine does not recover from and which therefore reaches no other meter —
     * {@link SecretResolutionMetricsDoc#RESOLUTION_CYCLE_ERROR} counts only the cycles the engine
     * carried on from.
     */
    ERROR
  }
}
