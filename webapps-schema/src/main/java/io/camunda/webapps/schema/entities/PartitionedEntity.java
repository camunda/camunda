/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

/**
 * Represents a partitioned entity
 *
 * @param <T>
 */
public interface PartitionedEntity<T extends PartitionedEntity<T>> {
  /**
   * @return the partition id of the entity
   */
  int getPartitionId();

  /**
   * Sets the partition id of the entity
   *
   * @param partitionId the id to set
   * @return the entity
   */
  T setPartitionId(final int partitionId);
}
