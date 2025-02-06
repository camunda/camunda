/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** {@link EngineMetricsDoc} documents all workflow engine specific metrics. */
@SuppressWarnings("NullableProblems")
public enum EngineMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of created (root) process instances */
  ROOT_PROCESS_INSTANCE_COUNT {
    private static final KeyName[] KEY_NAMES = new KeyName[] {EngineKeyNames.CREATION_MODE};

    @Override
    public String getName() {
      return "zeebe.process.instance.creations.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of created (root) process instances";
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of process element instance events */
  ELEMENT_INSTANCE_EVENTS {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          EngineKeyNames.ACTION, EngineKeyNames.ELEMENT_TYPE, EngineKeyNames.EVENT_TYPE
        };

    @Override
    public String getDescription() {
      return "Number of process element instance events";
    }

    @Override
    public String getName() {
      return "zeebe.element.instance.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of executed (root) process instances */
  EXECUTED_EVENTS {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          EngineKeyNames.ACTION, EngineKeyNames.ELEMENT_TYPE, EngineKeyNames.ORGANIZATION_ID
        };

    @Override
    public String getDescription() {
      return "Number of executed (root) process instances";
    }

    @Override
    public String getName() {
      return "zeebe.executed.instances.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },
  /** Number of evaluated DMN elements including required decisions */
  EVALUATED_DMN_ELEMENTS {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {EngineKeyNames.ACTION, EngineKeyNames.ORGANIZATION_ID};

    @Override
    public String getDescription() {
      return "Number of evaluated DMN elements including required decisions";
    }

    @Override
    public String getName() {
      return "zeebe.evaluated.dmn.elements.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  /** Tags/label values possibly used by the engine metrics. */
  public enum EngineKeyNames implements KeyName {
    /**
     * Specifies the way in which the root process instance was created, e.g. at a specific element,
     * at the default start event, etc.
     *
     * <p>See {@link CreationMode} for possible values.
     */
    CREATION_MODE {
      @Override
      public String asString() {
        return "creation_mode";
      }
    },

    /**
     * The processing action that modified the given series; see {@link EngineAction} for possible
     * values.
     */
    ACTION {
      @Override
      public String asString() {
        return "action";
      }
    },

    /**
     * The BPMN element type which trigger the modification of the given meter. See {@link
     * io.camunda.zeebe.protocol.record.value.BpmnElementType} for values.
     */
    ELEMENT_TYPE {
      @Override
      public String asString() {
        return "type";
      }
    },

    /**
     * The {@link io.camunda.zeebe.protocol.record.value.BpmnEventType} which triggered the
     * modification to the meter.
     */
    EVENT_TYPE {
      @Override
      public String asString() {
        return "eventType";
      }
    },

    /**
     * Metrics that are annotated with this label are vitally important for usage tracking and
     * data-based decision-making as part of Camunda's SaaS offering.
     *
     * <p>DO NOT REMOVE this label from existing metrics without previous discussion within the
     * team.
     *
     * <p>At the same time, NEW METRICS MAY NOT NEED THIS label. In that case, it is preferable to
     * not add this label to a metric as Prometheus best practices warn against using labels with a
     * high cardinality of possible values.
     */
    ORGANIZATION_ID {
      @Override
      public String asString() {
        return "organizationId";
      }
    }
  }

  public enum CreationMode {
    CREATION_AT_DEFAULT_START_EVENT,
    CREATION_AT_GIVEN_ELEMENT;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public enum EngineAction {
    ACTIVATED,
    COMPLETED,
    TERMINATED,
    EVALUATED_SUCCESSFULLY,
    EVALUATED_FAILED;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}
