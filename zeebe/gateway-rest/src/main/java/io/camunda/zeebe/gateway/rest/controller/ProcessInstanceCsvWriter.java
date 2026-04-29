/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.opencsv.CSVWriter;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

final class ProcessInstanceCsvWriter implements AutoCloseable {

  private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
  private static final String INCIDENT_STATE = "INCIDENT";

  private final CSVWriter csv;
  private final boolean multiTenancyEnabled;

  private ProcessInstanceCsvWriter(final CSVWriter csv, final boolean multiTenancyEnabled) {
    this.csv = csv;
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  static ProcessInstanceCsvWriter open(final OutputStream out, final boolean multiTenancyEnabled)
      throws IOException {
    out.write(UTF8_BOM);
    final var writer =
        new CSVWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    return new ProcessInstanceCsvWriter(writer, multiTenancyEnabled);
  }

  void writeHeader() {
    csv.writeNext(headerRow(multiTenancyEnabled));
  }

  void writeRow(final ProcessInstanceEntity entity) {
    csv.writeNext(rowFor(entity, multiTenancyEnabled));
  }

  void flush() throws IOException {
    csv.flush();
  }

  @Override
  public void close() throws IOException {
    csv.close();
  }

  static String[] headerRow(final boolean multiTenancyEnabled) {
    if (multiTenancyEnabled) {
      return new String[] {
        "Process Name",
        "Process Instance Key",
        "Version",
        "Version Tag",
        "Tenant",
        "State",
        "Start Date",
        "End Date",
        "Parent Process Instance Key"
      };
    }
    return new String[] {
      "Process Name",
      "Process Instance Key",
      "Version",
      "Version Tag",
      "State",
      "Start Date",
      "End Date",
      "Parent Process Instance Key"
    };
  }

  static String[] rowFor(final ProcessInstanceEntity e, final boolean multiTenancyEnabled) {
    final String name =
        e.processDefinitionName() != null ? e.processDefinitionName() : e.processDefinitionId();
    if (multiTenancyEnabled) {
      return new String[] {
        nullToEmpty(name),
        toStr(e.processInstanceKey()),
        toStr(e.processDefinitionVersion()),
        nullToEmpty(e.processDefinitionVersionTag()),
        nullToEmpty(e.tenantId()),
        stateLabel(e),
        formatDate(e.startDate()),
        formatDate(e.endDate()),
        toStr(e.parentProcessInstanceKey())
      };
    }
    return new String[] {
      nullToEmpty(name),
      toStr(e.processInstanceKey()),
      toStr(e.processDefinitionVersion()),
      nullToEmpty(e.processDefinitionVersionTag()),
      stateLabel(e),
      formatDate(e.startDate()),
      formatDate(e.endDate()),
      toStr(e.parentProcessInstanceKey())
    };
  }

  private static String stateLabel(final ProcessInstanceEntity e) {
    final ProcessInstanceState state = e.state();
    if (state == ProcessInstanceState.ACTIVE && Boolean.TRUE.equals(e.hasIncident())) {
      return INCIDENT_STATE;
    }
    return state != null ? state.name() : "";
  }

  private static String formatDate(final OffsetDateTime value) {
    return value == null ? "" : value.atZoneSameInstant(ZoneOffset.UTC).format(ISO_UTC);
  }

  private static String toStr(final Object value) {
    return value == null ? "" : value.toString();
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
