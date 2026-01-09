/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class FlowNodeFilter implements RecordValueFilter {

  private static final String LIST_SEPARATOR = ";";
  private final Set<BpmnElementType> inclusion;
  private final Set<BpmnElementType> exclusion;

  public FlowNodeFilter(final String inclusion, final String exclusion) {
    this.inclusion = inclusion == null ? Collections.emptySet() : parseTypes(inclusion);
    this.exclusion = exclusion == null ? Collections.emptySet() : parseTypes(exclusion);
  }

  @Override
  public boolean accept(final RecordValue value) {
    if (value instanceof final ProcessInstanceRecordValue processInstanceRecordValue) {
      final BpmnElementType bpmnElementType = processInstanceRecordValue.getBpmnElementType();

      if (!inclusion.isEmpty() && !inclusion.contains(bpmnElementType)) {
        return false;
      }

      return exclusion.isEmpty() || !exclusion.contains(bpmnElementType);
    }

    return true;
  }

  private Set<BpmnElementType> parseTypes(final String rawTypes) {
    if (rawTypes == null || rawTypes.isEmpty()) {
      return Collections.emptySet();
    }

    final Set<BpmnElementType> types = EnumSet.noneOf(BpmnElementType.class);
    Arrays.stream(rawTypes.split(LIST_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(s -> BpmnElementType.findBpmnElementTypeFor(s).ifPresent(types::add));

    return types;
  }
}
