/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.webapps.schema.entities.HistoryDeletionEntity;

/**
 * An operation to mark as completed, together with the history deletion entity that triggered it.
 * The entity tells the repository which index the operation lives in.
 *
 * @param entity the history deletion entity that was processed
 * @param id the id of the operation, following the pattern defined in {@link
 *     io.camunda.exporter.handlers.batchoperation.AbstractOperationHandler#ID_PATTERN}
 */
public record OperationToComplete(HistoryDeletionEntity entity, String id) {}
