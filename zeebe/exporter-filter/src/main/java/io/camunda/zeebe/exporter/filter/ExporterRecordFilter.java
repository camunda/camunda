/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;

/**
 * A filter to determine whether a given record should be exported or not.
 *
 * <p>Implementations of this interface can be used to filter records based on their content,
 * metadata, or any other criteria defined by the user.
 */
@FunctionalInterface
public interface ExporterRecordFilter {

  boolean accept(Record<?> record);
}
