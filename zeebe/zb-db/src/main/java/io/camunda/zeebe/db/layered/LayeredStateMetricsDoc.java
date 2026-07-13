/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Documentation of all meters published by the layered state store. */
@SuppressWarnings("NullableProblems")
public enum LayeredStateMetricsDoc implements ExtendedMeterDocumentation {
  /** Writes that never reached the durable store */
  WRITES_ELIDED {
    @Override
    public String getName() {
      return "zeebe.db.layered.writes.elided";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Writes that never reached the durable store: put/delete pairs annihilated in"
          + " memory and never-flushed tombstones skipped by a persist drain";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        LayeredStateKeyNames.DOMAIN, LayeredStateKeyNames.ELISION_REASON,
      };
    }
  },
  /** Persist rounds started, by trigger */
  PERSIST_ROUNDS {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.rounds";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Persist rounds started, by trigger (interval, over-capacity, pre-snapshot,"
          + " scheduled-task)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        LayeredStateKeyNames.DOMAIN, LayeredStateKeyNames.TRIGGER,
      };
    }
  },
  /** Persist rounds that failed and will be retried */
  PERSIST_FAILURES {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.failures";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Persist rounds that failed; the drained segments stay buffered and are retried";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Whether a persist round is currently in flight */
  PERSIST_INFLIGHT {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.inflight";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Whether a persist round of the domain is currently in flight (1) or not (0); rounds"
          + " are single-flight per domain";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Duration of the persist step of a round */
  PERSIST_DURATION {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Duration of the persist step of a round (draining all captured segments into one"
          + " atomic batch and committing it)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Entries drained to the durable store by persist rounds */
  DRAINED_ENTRIES {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.drained.entries";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Entries (puts and surviving tombstones) drained to the durable store by persist"
          + " rounds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Bytes drained to the durable store by persist rounds */
  DRAINED_BYTES {
    @Override
    public String getName() {
      return "zeebe.db.layered.persist.drained.bytes";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Key and value bytes drained to the durable store by persist rounds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Distance between the newest frozen watermark and the last persisted anchor */
  ANCHOR_LAG {
    @Override
    public String getName() {
      return "zeebe.db.layered.anchor.lag";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Log positions between the newest frozen segment watermark and the anchor of the"
          + " last successful persist round — how far the durable store trails the buffered"
          + " state";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Bytes held per in-memory layer */
  BUFFERED_BYTES {
    @Override
    public String getName() {
      return "zeebe.db.layered.buffered.bytes";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Approximate bytes held per in-memory layer, aggregated over the domain's stores";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        LayeredStateKeyNames.DOMAIN, LayeredStateKeyNames.LAYER,
      };
    }
  },
  /** Entries held per in-memory layer */
  BUFFERED_ENTRIES {
    @Override
    public String getName() {
      return "zeebe.db.layered.buffered.entries";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Entries held per in-memory layer, aggregated over the domain's stores";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        LayeredStateKeyNames.DOMAIN, LayeredStateKeyNames.LAYER,
      };
    }
  },
  /** Point reads answered below the pinned layers, by source */
  READS {
    @Override
    public String getName() {
      return "zeebe.db.layered.reads";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Point reads that fell below the pinned in-memory layers, answered by the clean"
          + " read-through cache or by a durable-store (delegate) read";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        LayeredStateKeyNames.DOMAIN, LayeredStateKeyNames.READ_SOURCE,
      };
    }
  },
  /** Delegate point reads made to compute exact flushed flags */
  FLUSHED_POINT_READS {
    @Override
    public String getName() {
      return "zeebe.db.layered.flushed.point.reads";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Durable-store point reads made at write time to compute the exact flushed flag of"
          + " a key unknown to every in-memory layer";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Deepest segment pipeline across the domain's stores */
  PIPELINE_DEPTH {
    @Override
    public String getName() {
      return "zeebe.db.layered.pipeline.depth";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Deepest frozen-segment pipeline across the domain's stores — the read"
          + " amplification bound";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** In-memory merges of pipeline segments */
  PIPELINE_MERGES {
    @Override
    public String getName() {
      return "zeebe.db.layered.pipeline.merges";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "In-memory merges collapsing a store's pipeline back under its segment limit";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Over-limit pipeline merges skipped for lack of annihilation */
  PIPELINE_MERGES_SKIPPED {
    @Override
    public String getName() {
      return "zeebe.db.layered.pipeline.merges.skipped";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Over-limit pipeline merges skipped because the store's last merge annihilated too"
          + " few entries to pay off; the pipeline overshoots to a hard cap instead";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Reader view rotations after successful persist rounds */
  VIEW_ROTATIONS {
    @Override
    public String getName() {
      return "zeebe.db.layered.view.rotations";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Reader view rotations onto a fresh durable-state snapshot after successful"
          + " persist rounds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  },
  /** Read-only views acquired by concurrent readers */
  VIEW_ACQUISITIONS {
    @Override
    public String getName() {
      return "zeebe.db.layered.view.acquisitions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Read-only views acquired by concurrent readers through the domain's view"
          + " publisher";
    }

    @Override
    public KeyName[] getKeyNames() {
      return DOMAIN_ONLY;
    }
  };

  private static final KeyName[] DOMAIN_ONLY = new KeyName[] {LayeredStateKeyNames.DOMAIN};

  /** Tag keys of the layered state meters. */
  @SuppressWarnings("NullableProblems")
  public enum LayeredStateKeyNames implements KeyName {
    /** The ownership domain the meter aggregates over (e.g. {@code engine}) */
    DOMAIN {
      @Override
      public String asString() {
        return "domain";
      }
    },
    /** The in-memory layer, one of {@link Layer} */
    LAYER {
      @Override
      public String asString() {
        return "layer";
      }
    },
    /** What triggered a persist round, one of {@link PersistTrigger} */
    TRIGGER {
      @Override
      public String asString() {
        return "trigger";
      }
    },
    /** Why a write never reached the durable store, one of {@link ElisionReason} */
    ELISION_REASON {
      @Override
      public String asString() {
        return "reason";
      }
    },
    /** What answered a read below the pinned layers, one of {@link ReadSource} */
    READ_SOURCE {
      @Override
      public String asString() {
        return "source";
      }
    }
  }

  /** Values of the {@link LayeredStateKeyNames#LAYER} tag. */
  public enum Layer {
    STAGING("staging"),
    ACTIVE("active"),
    PIPELINE("pipeline"),
    CLEAN("clean");

    private final String label;

    Layer(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** Values of the {@link LayeredStateKeyNames#ELISION_REASON} tag. */
  public enum ElisionReason {
    ANNIHILATED("annihilated"),
    DRAIN_SKIPPED("drainSkipped");

    private final String label;

    ElisionReason(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  /** Values of the {@link LayeredStateKeyNames#READ_SOURCE} tag. */
  public enum ReadSource {
    CLEAN_CACHE("cleanCache"),
    DELEGATE("delegate");

    private final String label;

    ReadSource(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }
}
