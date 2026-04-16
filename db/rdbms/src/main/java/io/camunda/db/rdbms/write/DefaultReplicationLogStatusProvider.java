/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultReplicationLogStatusProvider implements ReplicationLogStatusProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultReplicationLogStatusProvider.class);
  private ReplicationLogStatusProvider delegate;
  private final List<ReplicationLogStatusProvider> delegates;

  public DefaultReplicationLogStatusProvider(final ExporterPositionMapper exporterPositionMapper) {
    delegates =
        List.of(
            new PostgresReplicationLogStatusProvider(exporterPositionMapper),
            new AuroraReplicationLogStatusProvider(exporterPositionMapper));
    try {
      setupDelegates();
    } catch (final Exception e) {
      LOG.warn("Failed to setup delegates, will retry later", e);
    }
    LOG.debug("Starting with ReplicationLogStatusProvider {}", delegate);
  }

  @Override
  public long getCurrent() {
    trySetupDelegates();
    return delegate.getCurrent();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    trySetupDelegates();
    return delegate.getReplicationStatuses();
  }

  @Override
  public Duration getReplicationLag() {
    trySetupDelegates();
    return delegate.getReplicationLag();
  }

  private void setupDelegates() {
    for (final var del : delegates) {
      try {
        del.getCurrent();
        delegate = del;
        return;
      } catch (final Exception e) {
        LOG.debug("Delegate {} failed, skipping it.", del);
      }
    }
    final var errorMsg = String.format(delegates.toString());
    LOG.error(errorMsg);
    throw new IllegalStateException(errorMsg);
  }

  private void trySetupDelegates() {
    if (delegate == null) {
      setupDelegates();
    }
  }
}
