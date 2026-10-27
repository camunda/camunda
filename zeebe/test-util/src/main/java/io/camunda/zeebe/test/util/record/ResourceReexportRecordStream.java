/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceReexportRecordValue;
import java.util.stream.Stream;

public class ResourceReexportRecordStream
    extends ExporterRecordStream<ResourceReexportRecordValue, ResourceReexportRecordStream> {

  public ResourceReexportRecordStream(
      final Stream<Record<ResourceReexportRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ResourceReexportRecordStream supply(
      final Stream<Record<ResourceReexportRecordValue>> wrappedStream) {
    return new ResourceReexportRecordStream(wrappedStream);
  }
}
