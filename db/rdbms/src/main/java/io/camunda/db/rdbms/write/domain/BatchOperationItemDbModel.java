/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.BatchOperationEntity;
import java.time.OffsetDateTime;

public record BatchOperationItemDbModel(
    String batchOperationKey,
    long itemKey,
    long processInstanceKey,
    BatchOperationEntity.BatchOperationItemState state,
    OffsetDateTime processedDate,
    String errorMessage) {}
