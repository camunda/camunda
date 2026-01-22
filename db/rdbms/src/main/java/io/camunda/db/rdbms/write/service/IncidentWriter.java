/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentWriter.class);

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public IncidentWriter(
      final ExecutionQueue executionQueue,
      final IncidentMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final IncidentDbModel incident) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.INCIDENT,
            WriteStatementType.INSERT,
            incident.incidentKey(),
            "io.camunda.db.rdbms.sql.IncidentMapper.insert",
            incident.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void update(final IncidentDbModel incident) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.INCIDENT,
            WriteStatementType.UPDATE,
            incident.incidentKey(),
            "io.camunda.db.rdbms.sql.IncidentMapper.update",
            incident.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void resolve(final Long incidentKey) {
    final boolean wasMerged =
        mergeToQueue(incidentKey, b -> b.state(IncidentState.RESOLVED).errorMessage(null));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.INCIDENT,
              WriteStatementType.UPDATE,
              incidentKey,
              "io.camunda.db.rdbms.sql.IncidentMapper.updateState",
              new IncidentMapper.IncidentStateDto(
                  incidentKey, IncidentState.RESOLVED, null, null)));
    }
  }

  private boolean mergeToQueue(final long key, final Function<Builder, Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.INCIDENT, key, IncidentDbModel.class, mergeFunction));
  }
}
