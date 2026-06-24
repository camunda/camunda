/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import java.util.stream.Stream;

public class MappingRuleRecordStream
    extends ExporterRecordStream<MappingRuleRecordValue, MappingRuleRecordStream> {

  public MappingRuleRecordStream(final Stream<Record<MappingRuleRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MappingRuleRecordStream supply(
      final Stream<Record<MappingRuleRecordValue>> wrappedStream) {
    return new MappingRuleRecordStream(wrappedStream);
  }

  public MappingRuleRecordStream withMappingRuleId(final String mappingRuleId) {
    return valueFilter(v -> v.getMappingRuleId().equals(mappingRuleId));
  }

  public MappingRuleRecordStream withClaimName(final String claimName) {
    return valueFilter(v -> v.getClaimName().equals(claimName));
  }

  public MappingRuleRecordStream withClaimValue(final String claimValue) {
    return valueFilter(v -> v.getClaimValue().equals(claimValue));
  }
}
