/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;

/**
 * Documents the various generic exporter related metrics as used by the {@link ExporterDirector}.
 */
@SuppressWarnings("NullableProblems")
public enum ExporterMetricsDoc implements ExtendedMeterDocumentation {
  /** The last exported position by exporter and partition */
  LAST_EXPORTED_POSITION {
    @Override
    public String getName() {
      return "zeebe.exporter.last.exported.position";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The last exported position by exporter and partition";
    }
  },

  /** The last exported position which was also updated/committed by the exporter */
  LAST_UPDATED_EXPORTED_POSITION {
    @Override
    public String getName() {
      return "zeebe.exporter.last.updated.exported.position";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The last exported position which was also updated/committed by the exporter";
    }
  },

  /**
   * Describes the phase of the exporter, namely if it is exporting, paused or soft paused; valid
   * values are those found in {@link ExporterPhase}
   */
  EXPORTER_STATE {
    @Override
    public String getName() {
      return "zeebe.exporter.state";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return """
        Describes the phase of the exporter, namely if it is exporting (0), paused (1), soft \
        paused (2), or closed (3)""";
    }
  },

  /** Time between a record is written until it is picked up for exporting (in seconds). */
  EXPORTING_LATENCY {
    @Override
    public String getName() {
      return "zeebe.exporting.latency";
    }

    @Override
    public Meter.Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time between a record is written until it is picked up for exporting (in seconds)";
    }
  },

  /** The time an exporter needs to export certain record (duration in seconds) */
  EXPORTING_DURATION {
    @Override
    public String getName() {
      return "zeebe.exporter.exporting.duration";
    }

    @Override
    public Meter.Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "The time an exporter needs to export certain record (duration in seconds)";
    }
  },

  /** Number of events processed by exporter by action (see {@link ExporterActionKeyNames} */
  EXPORTER_EVENTS {
    @Override
    public String getName() {
      return "zeebe.exporter.events.total";
    }

    @Override
    public Meter.Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of events processed by exporter";
    }

    @Override
    public KeyName[] getKeyNames() {
      return ExporterActionKeyNames.values();
    }
  };

  public enum ExporterContainerKeyNames implements KeyName {
    /** Identifies which exporter is emitting a metric */
    EXPORTER_ID {
      @Override
      public String asString() {
        return "exporterId";
      }
    }
  }

  enum ExporterActionKeyNames implements KeyName {
    SKIPPED {
      @Override
      public String asString() {
        return "skipped";
      }
    },

    EXPORTED {
      @Override
      public String asString() {
        return "exported";
      }
    }
  }
}
