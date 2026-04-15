/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class DefaultReplicationLsnProvider implements ReplicationLsnProvider {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultReplicationLsnProvider.class);
  private ReplicationLsnProvider delegate;
  private final List<ReplicationLsnProvider> delegates;

  public DefaultReplicationLsnProvider(final ExporterPositionMapper exporterPositionMapper) {
    delegates =
        List.of(
            new PostgresReplicationLsnProvider(exporterPositionMapper),
            new AuroraReplicationLsnProvider(exporterPositionMapper));
    try {
      setupDelegates();
    } catch (final Exception e) {
      LOG.warn("Failed to setup delegates, will retry later", e);
    }
    LOG.debug("Starting with ReplicationLsnProvider {}", delegate);
  }

  @Override
  public long getCurrentLsn() {
    trySetupDelegates();
    return delegate.getCurrentLsn();
  }

  @Override
  public List<ReplicationStatusDto> getReplicationStatuses() {
    trySetupDelegates();
    return delegate.getReplicationStatuses();
  }

  private void setupDelegates() {
    for (final var del : delegates) {
      try {
        del.getCurrentLsn();
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
