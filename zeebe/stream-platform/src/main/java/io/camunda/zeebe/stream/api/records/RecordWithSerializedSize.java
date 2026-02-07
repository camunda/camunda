/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.records;

/**
 * Internal interface for records that can provide their serialized size. This interface is used for
 * performance optimizations in exporters and is not part of the public API.
 */
public interface RecordWithSerializedSize {

  /**
   * Returns the serialized size of the record in bytes. This includes both the metadata and value
   * portions of the record.
   *
   * @return the total serialized size of the record in bytes
   */
  int getSerializedSize();
}
