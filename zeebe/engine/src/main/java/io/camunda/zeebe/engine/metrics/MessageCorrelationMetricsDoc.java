/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/**
 * Documents the meters that instrument the Business-ID message-start correlation feature: the
 * cross-partition uniqueness handshake, the pending-ask retry/back-off, and the correlation-key
 * lock-release path.
 *
 * <p>The {@code partition} tag is added automatically by the per-partition meter registry, so the
 * engine code never sets it; every meter here documents it via {@link #getAdditionalKeyNames()}.
 * All other tags are closed enums (see the nested value enums) — no user-supplied value (business
 * id, correlation key, message name, tenant) is ever used as a tag.
 */
public enum MessageCorrelationMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Counts the outcomes of the cross-partition message-start REQUEST decision ladder on {@code P_B
   * = hash(businessId)} — the "cross-partition uniqueness results" of the handshake.
   */
  CROSS_PARTITION_REQUESTS {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.requests.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the outcomes of the cross-partition message-start REQUEST decision ladder on the business-id partition (P_B).";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessageCorrelationKeyNames.OUTCOME};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Counts the cross-partition reply outcomes processed on {@code P_K = hash(correlationKey)}. A
   * gap between {@link #CROSS_PARTITION_REQUESTS} and this meter indicates inter-partition reply
   * loss (which the retry then papers over).
   */
  CROSS_PARTITION_REPLIES {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.replies.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the cross-partition message-start reply outcomes processed on the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessageCorrelationKeyNames.OUTCOME};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Counts newly-registered cross-partition message-start asks dispatched from {@code P_K}. */
  CROSS_PARTITION_ASKS {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.asks.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the newly-registered cross-partition message-start asks dispatched from the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Counts cross-partition message-start ask retries sent by the pending-ask scheduler on {@code
   * P_K}; every scheduler send is by definition a retry (the initial send is counted by {@link
   * #CROSS_PARTITION_ASKS}).
   */
  CROSS_PARTITION_ASK_RETRIES {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.asks.retries.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the cross-partition message-start ask retries sent by the pending-ask scheduler on the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Counts correlation-key lock-release reconciliation queries dispatched from {@code P_K}. */
  LOCK_RELEASE_QUERIES {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.lock.release.queries.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the correlation-key lock-release reconciliation queries dispatched from the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Distribution of the number of holders per correlation-key lock-release reconciliation query. A
   * p-max pinned at the batch limit reveals reconciliation saturation.
   */
  LOCK_RELEASE_QUERY_BATCH_SIZE {
    private static final double[] BUCKETS = {1, 8, 16, 32, 64, 128};

    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.lock.release.query.batch.size";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public String getDescription() {
      return "Distribution of the number of holders per correlation-key lock-release reconciliation query dispatched from P_K.";
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Counts expired cross-partition message-start dedup entries swept on {@code P_B}. */
  DEDUP_SWEPT {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.dedup.swept.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the expired cross-partition message-start dedup entries swept on the business-id partition (P_B).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Counts correlation-key lock-release commands sent to {@code P_K}, tagged by whether they
   * originate from the push fast-path ({@code P_B} holder completion) or the reconciliation poll. A
   * rising {@code reconciliation} rate means pushes are being lost (ADR 0001, Consequences).
   */
  LOCK_RELEASES_SENT {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.lock.releases.sent.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the correlation-key lock-release commands sent to P_K, tagged by push fast-path vs reconciliation poll.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessageCorrelationKeyNames.TRIGGER};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Counts correlation-key lock-release outcomes processed on {@code P_K}: an actual release, or a
   * redundant reply (the lock was already released or re-acquired — a push-vs-poll race or stale
   * reply).
   */
  LOCK_RELEASES {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.lock.releases.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Counts the correlation-key lock-release outcomes processed on P_K, tagged released vs redundant.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessageCorrelationKeyNames.RESULT};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Current number of pending cross-partition message-start asks awaiting a reply on {@code P_K}.
   * Unlike the ask counters this is a live level, seeded from persisted state on recovery: a value
   * that stays elevated points at asks that never get a terminal reply (the blocked-start symptom
   * spike #58900 investigates).
   */
  CROSS_PARTITION_ASKS_PENDING {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.asks.pending";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Current number of pending cross-partition message-start asks awaiting a reply on the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Current number of held cross-partition message-start locks on {@code P_K} — correlation keys
   * reserved by a started cross-partition instance that has not yet released them. A level that
   * only grows points at releases that never arrive (lost push and lost reconciliation), which
   * would block further starts on those keys.
   */
  CROSS_PARTITION_LOCKS {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.locks";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Current number of held cross-partition message-start correlation-key locks on the correlation-key partition (P_K).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Tracks how many buffered messages on this partition carry a business id and are therefore
   * indexed for the cross-partition message-start uniqueness handshake. A same-partition start that
   * is skipped on uniqueness is re-found through this index once the holder frees the business id,
   * so this is the subset of {@code zeebe.buffered.messages.count} that participates in the
   * handshake. Watching it against the total buffer shows how much of the buffer the feature is
   * responsible for, and a level that only grows points at business-id index rows that are never
   * cleaned up.
   */
  CROSS_PARTITION_BUFFERED_MESSAGES {
    @Override
    public String getName() {
      return "zeebe.buffered.messages.business.id.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Current number of buffered messages indexed by business id on the message partition for the cross-partition message-start uniqueness handshake.";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Tracks how many cross-partition message-start dedup entries are currently outstanding on {@code
   * P_B = hash(businessId)}. Each entry records a uniqueness decision so a retried request from
   * {@code P_K} is answered with the original result instead of starting a duplicate; it is removed
   * once swept after its deadline. A level that only grows points at dedup rows that never expire —
   * the sweep not keeping up — which both retains stale uniqueness decisions and grows {@code
   * P_B}'s state unboundedly.
   */
  CROSS_PARTITION_DEDUP_ENTRIES {
    @Override
    public String getName() {
      return "zeebe.message.start.cross.partition.dedup.entries";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Current number of outstanding cross-partition message-start dedup entries on the business-id partition (P_B).";
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Counts message-start correlations left buffered because an active holder blocks them on the
   * message partition: a live publish/correlate finds either the correlation key already taken by
   * an active instance or the business id already held for the process definition, so no new
   * instance is started and the message stays buffered until the holder frees it. The {@code
   * reason} tag attributes each block to the gate that fired — the correlation key takes precedence
   * — so an operator can tell correlation-key contention apart from business-id uniqueness
   * back-pressure. A sustained rate with no matching drain (started instances) points at holders
   * that never release.
   */
  MESSAGE_START_BLOCKED {
    @Override
    public String getName() {
      return "zeebe.message.start.blocked.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of message-start correlations left buffered by an active holder on the message partition, tagged by the uniqueness gate that blocked.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MessageCorrelationKeyNames.REASON};
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  public enum MessageCorrelationKeyNames implements KeyName {
    /** The outcome of a cross-partition request or reply. */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    },

    /** What triggered a correlation-key lock release (push vs reconciliation). */
    TRIGGER {
      @Override
      public String asString() {
        return "trigger";
      }
    },

    /** The result of processing a correlation-key lock-release reply (released vs redundant). */
    RESULT {
      @Override
      public String asString() {
        return "result";
      }
    },

    /** Which uniqueness gate blocked a message-start correlation (correlation key, business id). */
    REASON {
      @Override
      public String asString() {
        return "reason";
      }
    };
  }

  /**
   * Outcomes of the cross-partition REQUEST decision ladder on {@code P_B} ({@code outcome} tag).
   */
  public enum RequestOutcome {
    STARTED("started"),
    DEDUP_HIT("dedup_hit"),
    REJECTED_UNIQUENESS("rejected_uniqueness"),
    REJECTED_NO_SUBSCRIPTION("rejected_no_subscription"),
    REJECTED_NOT_ACTIVATABLE("rejected_not_activatable"),
    REJECTED_EXPIRED("rejected_expired");

    private final String label;

    RequestOutcome(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** Outcomes of the cross-partition reply processors on {@code P_K} ({@code outcome} tag). */
  public enum ReplyOutcome {
    STARTED("started"),
    REJECTED_UNIQUENESS("rejected_uniqueness"),
    REJECTED_NO_SUBSCRIPTION("rejected_no_subscription"),
    REJECTED_EXPIRED("rejected_expired");

    private final String label;

    ReplyOutcome(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** What triggered a correlation-key lock release ({@code trigger} tag). */
  public enum ReleaseTrigger {
    PUSH("push"),
    RECONCILIATION("reconciliation");

    private final String label;

    ReleaseTrigger(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** The result of processing a correlation-key lock-release reply ({@code result} tag). */
  public enum ReleaseResult {
    RELEASED("released"),
    REDUNDANT("redundant");

    private final String label;

    ReleaseResult(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** Which uniqueness gate blocked a message-start correlation ({@code reason} tag). */
  public enum BlockReason {
    CORRELATION_KEY("correlation_key"),
    BUSINESS_ID("business_id");

    private final String label;

    BlockReason(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }
}
