/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.Optional;

/**
 * Represents the state of an exporter. The full configuration of this exporter must be provided as
 * part of the application configuration. Here we only keep track of whether it is enabled or
 * disabled. Sensitive information like access details must not be added here.
 *
 * @param metadataVersion the version of the metadata that is stored in the exporter runtime state.
 *     This is incremented when ever the exporter is re-enabled.
 * @param state the state of the exporter
 * @param initializedFrom the id of the exporter that the metadata of this exporter was initialized
 *     from.
 */
public record ExporterState(long metadataVersion, State state, Optional<String> initializedFrom) {
  public enum State {
    /** The records will be exported to this exporter */
    ENABLED,
    /**
     * The records are not exported to this exporter. But the ExporterMetadata of this exporter is
     * persisted in the cluster. When this exporter is re-enabled, it will start exporting new
     * records and reuse the previous metadata. Note that the records created while this exporter is
     * disabled may not be available to export to this exporter anymore.
     */
    DISABLED,
    /**
     * The exporter is still enabled, but unable to export because it has been removed from the
     * application properties.
     */
    CONFIG_NOT_FOUND,
  }
}
