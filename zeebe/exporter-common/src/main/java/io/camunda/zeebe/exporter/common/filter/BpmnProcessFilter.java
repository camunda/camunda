/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnProcessRelated;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public final class BpmnProcessFilter implements RecordValueFilter {

  private static final String LIST_SEPARATOR = ";";
  private final Set<String> inclusion;
  private final Set<String> exclusion;

  public BpmnProcessFilter(final String inclusion, final String exclusion) {
    this.inclusion = inclusion == null ? Collections.emptySet() : parseBpmnProcessIds(inclusion);
    this.exclusion = exclusion == null ? Collections.emptySet() : parseBpmnProcessIds(exclusion);
  }

  @Override
  public boolean accept(final RecordValue value) {
    if (value instanceof final BpmnProcessRelated bpmnProcessRelated) {
      final String bpmnProcessId = bpmnProcessRelated.getBpmnProcessId();

      if (!inclusion.isEmpty() && !inclusion.contains(bpmnProcessId)) {
        return false;
      }

      return exclusion.isEmpty() || !exclusion.contains(bpmnProcessId);
    }

    return true;
  }

  private Set<String> parseBpmnProcessIds(final String rawBpmnProcessIds) {
    if (rawBpmnProcessIds == null || rawBpmnProcessIds.isEmpty()) {
      return Collections.emptySet();
    }

    return Arrays.stream(rawBpmnProcessIds.split(LIST_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }
}
