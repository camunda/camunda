/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import io.camunda.debug.cli.ProcessInstanceRelatedValue;
import java.util.ArrayList;
import java.util.List;

public class LogContent {
  public final List<PersistedRecord> records = new ArrayList<>();

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{ \"records\": [");
    for (int i = 0; i < records.size(); i++) {
      sb.append(records.get(i));
      if (i < records.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append("] }");
    return sb.toString();
  }

  public String asDotFile() {
    final StringBuilder content =
        new StringBuilder("digraph log {")
            .append(System.lineSeparator())
            .append("rankdir=\"RL\"")
            .append(";")
            .append(System.lineSeparator());

    for (final PersistedRecord rec : records) {
      if (rec instanceof final ApplicationRecord appRec) {
        for (final Record entry : appRec.entries) {
          addEventAsDotNode(entry, content);
        }
      }
    }
    content.append(System.lineSeparator()).append("}");
    return content.toString();
  }

  private void addEventAsDotNode(final Record entry, final StringBuilder content) {
    content
        .append(entry.position())
        .append(" [label=\"")
        .append("\\n")
        .append(entry.recordType())
        .append("\\n")
        .append(entry.valueType() != null ? entry.valueType().toString() : "")
        .append("\\n")
        .append(entry.intent() != null ? entry.intent().toString() : "");

    if ("PROCESS_INSTANCE".equals(String.valueOf(entry.valueType()))) {
      final ProcessInstanceRelatedValue piRelatedValue = entry.piRelatedValue();
      if (piRelatedValue != null) {
        if (piRelatedValue.bpmnElementType != null) {
          content.append("\\n").append(piRelatedValue.bpmnElementType);
        }
        if (piRelatedValue.processInstanceKey != null) {
          content.append("\\nPI Key: ").append(piRelatedValue.processInstanceKey);
        }
        if (piRelatedValue.processDefinitionKey != null) {
          content.append("\\nPD Key: ").append(piRelatedValue.processDefinitionKey);
        }
      }
    }
    content.append("\\nKey: ").append(entry.key()).append("\"];").append(System.lineSeparator());
    if (entry.sourceRecordPosition() != -1L) {
      content
          .append(entry.position())
          .append(" -> ")
          .append(entry.sourceRecordPosition())
          .append(";")
          .append(System.lineSeparator());
    }
  }
}
