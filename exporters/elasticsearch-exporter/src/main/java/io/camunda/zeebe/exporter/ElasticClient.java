/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;

/** A thin client to interface with an Elastic instance. */
public interface ElasticClient extends AutoCloseable {

  /**
   * Buffers the given record in memory for indexing on flush.
   *
   * @param record the record to buffer
   */
  void index(Record<?> record);

  /** Flushes all buffered records so that they're indexed in Elastic. */
  void flush();

  /** Returns true if the client should flush, false otherwise. */
  boolean shouldFlush();

  /**
   * Creates a new index template for records of the given {@link ValueType} on the target Elastic
   * instance.
   *
   * <p>Returns true if Elastic acknowledged that the template was created.
   */
  boolean putIndexTemplate(ValueType valueType);

  /**
   * Creates a new component template for records on the target Elastic instance.
   *
   * <p>Returns true if Elastic acknowledged that the template was created.
   */
  boolean putComponentTemplate();
}
