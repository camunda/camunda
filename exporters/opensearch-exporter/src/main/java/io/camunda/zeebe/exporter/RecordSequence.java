/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

/**
 * The record sequence is a combination of the partition id and the counter.
 *
 * <p>The Opensearch exporter puts the sequence together with the record in the index. It can be
 * used to limit the number of records when reading from the index, for example, by using a range
 * query.
 *
 * @param partitionId the partition id of the record
 * @param counter the counter based on the record's value type
 * @see <a href="https://github.com/camunda/zeebe/issues/10568">Related issue</a>
 */
public record RecordSequence(int partitionId, long counter) {

  /**
   * The record sequence is calculated based on the following formula.
   *
   * <pre>
   *   ((long) partitionId << 51) + counter
   * </pre>
   *
   * @return the record sequence
   */
  public long sequence() {
    return ((long) partitionId << 51) + counter;
  }
}
