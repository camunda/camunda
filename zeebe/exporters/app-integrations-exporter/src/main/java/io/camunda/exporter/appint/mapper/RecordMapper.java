/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.mapper;

import io.camunda.zeebe.protocol.record.Record;

public interface RecordMapper<T> {
  boolean supports(final Record<?> record);

  T map(final Record<?> record);
}
