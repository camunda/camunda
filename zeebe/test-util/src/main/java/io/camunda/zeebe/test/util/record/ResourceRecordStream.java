/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import java.util.stream.Stream;

public class ResourceRecordStream extends ExporterRecordStream<Resource, ResourceRecordStream> {

  public ResourceRecordStream(final Stream<Record<Resource>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ResourceRecordStream supply(final Stream<Record<Resource>> wrappedStream) {
    return new ResourceRecordStream(wrappedStream);
  }

  public ResourceRecordStream withResourceId(final String formId) {
    return valueFilter(v -> v.getResourceId().equals(formId));
  }

  public ResourceRecordStream withResourceKey(final long formKey) {
    return valueFilter(v -> v.getResourceKey() == formKey);
  }

  public ResourceRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }
}
