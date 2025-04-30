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

public enum DistributionMetricsDoc implements ExtendedMeterDocumentation {
  COMMAND_DISTRIBUTIONS {
    @Override
    public String getDescription() {
      return "Counts the number of command distributions.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  ACTIVE_COMMAND_DISTRIBUTIONS {
    @Override
    public String getDescription() {
      return "Tracks the number of currently active command distributions.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.active";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  PENDING_COMMAND_DISTRIBUTIONS {
    public static final KeyName[] PARTITION_KEY_NAMES = {PartitionKeyNames.TARGET_PARTITION};

    @Override
    public String getDescription() {
      return "Tracks the number of currently pending command distributions.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.pending";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PARTITION_KEY_NAMES;
    }
  },

  INFLIGHT_COMMAND_DISTRIBUTIONS {
    public static final KeyName[] PARTITION_KEY_NAMES = {PartitionKeyNames.TARGET_PARTITION};

    @Override
    public String getDescription() {
      return "Tracks the number of currently inflight command distributions.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.inflight";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PARTITION_KEY_NAMES;
    }
  },

  RETRY_INFLIGHT_COMMAND_DISTRIBUTIONS {
    public static final KeyName[] PARTITION_KEY_NAMES = {PartitionKeyNames.TARGET_PARTITION};

    @Override
    public String getDescription() {
      return "Counts the number of retried inflight command distributions.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.inflight.retries";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PARTITION_KEY_NAMES;
    }
  },

  RECEIVED_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS {
    public static final KeyName[] PARTITION_KEY_NAMES = {PartitionKeyNames.TARGET_PARTITION};

    @Override
    public String getDescription() {
      return "Counts the number of received acknowledgements from the target partition.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.acknowledged.received";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PARTITION_KEY_NAMES;
    }
  },

  SENT_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS {
    public static final KeyName[] PARTITION_KEY_NAMES = {PartitionKeyNames.TARGET_PARTITION};

    @Override
    public String getDescription() {
      return "Counts the number of sent acknowledgements to the target partition.";
    }

    @Override
    public String getName() {
      return "zeebe.command.distributions.acknowledged.sent";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PARTITION_KEY_NAMES;
    }
  }
}
