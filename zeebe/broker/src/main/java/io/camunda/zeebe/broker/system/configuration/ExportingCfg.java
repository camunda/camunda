/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.util.Objects;
import java.util.Set;

/**
 * Exporting component configuration. This configuration pertains to configurations that are common
 * to all exporters.
 */
public final class ExportingCfg implements ConfigurationEntry {
  private Set<Long> skipRecords;

  public Set<Long> getSkipRecords() {
    return skipRecords != null ? skipRecords : Set.of();
  }

  public void setSkipRecords(final Set<Long> skipRecords) {
    this.skipRecords = skipRecords;
  }

  @Override
  public int hashCode() {
    return Objects.hash(skipRecords);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExportingCfg that = (ExportingCfg) o;
    return Objects.equals(skipRecords, that.skipRecords);
  }

  @Override
  public String toString() {
    return "ExporterCfg{" + "skipRecords='" + skipRecords + '}';
  }
}
