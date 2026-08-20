/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

/**
 * Represents an entity that can be written to ElasticSearch or OpenSearch
 *
 * @param <T>
 */
public interface ExporterEntity<T extends ExporterEntity<T>> {

  /**
   * @return the id of the entity
   */
  String getId();

  /**
   * Sets the id of the entity
   *
   * @param id the id to set
   * @return the entity
   */
  T setId(final String id);
}
