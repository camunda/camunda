/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
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
  // CRLF matches Excel-on-Windows expectations and the existing Optimize CSV writer; OpenCSV
  // defaults to LF only.
  private static final String LINE_END = "\r\n";

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
        new CSVWriter(
            new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)),
            ICSVWriter.DEFAULT_SEPARATOR,
            ICSVWriter.DEFAULT_QUOTE_CHARACTER,
            ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
            LINE_END);
    return new ProcessInstanceCsvWriter(writer, multiTenancyEnabled);
  }

  void writeHeader() {
    csv.writeNext(headerRow(multiTenancyEnabled));
  }

  void writeRow(
      final ProcessInstanceEntity entity,
      final String incidentMessage,
      final String variablesJson) {
    final String[] row = rowFor(entity, multiTenancyEnabled, incidentMessage, variablesJson);
    for (int i = 0; i < row.length; i++) {
      row[i] = sanitizeForCsv(row[i]);
    }
    csv.writeNext(row);
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
        "Business Key",
        "Version",
        "Version Tag",
        "Tenant",
        "State",
        "Incident Message",
        "Start Date",
        "End Date",
        "Parent Process Instance Key",
        "Variables"
      };
    }
    return new String[] {
      "Process Name",
      "Process Instance Key",
      "Business Key",
      "Version",
      "Version Tag",
      "State",
      "Incident Message",
      "Start Date",
      "End Date",
      "Parent Process Instance Key",
      "Variables"
    };
  }

  static String[] rowFor(
      final ProcessInstanceEntity e,
      final boolean multiTenancyEnabled,
      final String incidentMessage,
      final String variablesJson) {
    final String name =
        e.processDefinitionName() != null ? e.processDefinitionName() : e.processDefinitionId();
    if (multiTenancyEnabled) {
      return new String[] {
        nullToEmpty(name),
        toStr(e.processInstanceKey()),
        nullToEmpty(e.businessId()),
        toStr(e.processDefinitionVersion()),
        nullToEmpty(e.processDefinitionVersionTag()),
        nullToEmpty(e.tenantId()),
        stateLabel(e),
        nullToEmpty(incidentMessage),
        formatDate(e.startDate()),
        formatDate(e.endDate()),
        toStr(e.parentProcessInstanceKey()),
        nullToEmpty(variablesJson)
      };
    }
    return new String[] {
      nullToEmpty(name),
      toStr(e.processInstanceKey()),
      nullToEmpty(e.businessId()),
      toStr(e.processDefinitionVersion()),
      nullToEmpty(e.processDefinitionVersionTag()),
      stateLabel(e),
      nullToEmpty(incidentMessage),
      formatDate(e.startDate()),
      formatDate(e.endDate()),
      toStr(e.parentProcessInstanceKey()),
      nullToEmpty(variablesJson)
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

  /**
   * Defeats CSV-formula-injection attacks. Spreadsheet apps interpret a leading {@code =}, {@code
   * +}, {@code -}, {@code @}, tab, or carriage-return as the start of a formula, which can leak
   * data or fire HTTP requests when the export is opened. Prepending a single quote forces the cell
   * to be treated as text (the quote is not displayed by Excel/LibreOffice/Sheets). See OWASP "CSV
   * Injection" for background.
   */
  static String sanitizeForCsv(final String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    final char first = value.charAt(0);
    if (first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r') {
      return "'" + value;
    }
    return value;
  }
}
