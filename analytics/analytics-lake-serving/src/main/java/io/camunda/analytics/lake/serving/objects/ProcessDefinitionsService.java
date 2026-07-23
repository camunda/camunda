/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.objects;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Backs {@code GET /api/definitions}: looks up one deployed process definition's BPMN 2.0 XML, and
 * {@code GET /api/definitions/list}: the distinct process ids with their deployed versions -- both
 * from the {@code process_definitions} dictionary table (a lake writer table this module only reads
 * -- it never deploys or modifies definitions).
 *
 * <h2>Open/closed (read-time degradation)</h2>
 *
 * <p>{@code process_definitions} may not exist yet in an older warehouse (this table is newer than
 * {@code objects}/{@code object_relations}). Same rule as {@link ObjectsService}: never a 500 for a
 * missing view. For {@link #find}, both "view absent" and "no row for this (processId, version)"
 * surface as {@link NoSuchElementException}, which {@code ProcessDefinitionsController} maps to a
 * 404 -- the caller (the object detail page's Diagram tab) treats any error response the same way:
 * hide the tab. {@link #list} degrades differently on a missing view -- an empty {@code
 * definitions} list rather than an error -- since its caller (the Processes page's picker) renders
 * an empty state, not an error state.
 */
@Service
public class ProcessDefinitionsService {

  private static final String PROCESS_DEFINITIONS = "process_definitions";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ProcessDefinitionsService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public ProcessDefinitionResult find(final String processId, final int version) {
    if (processId == null || processId.isBlank()) {
      throw new IllegalArgumentException("processId is required");
    }
    if (!viewRegistry.ensureAvailable(PROCESS_DEFINITIONS)) {
      throw new NoSuchElementException(
          "No process definitions are available in this warehouse yet");
    }
    // The lake stores bpmn_xml as BINARY (unique-per-row blobs must not be dictionary-encoded);
    // decode() reads the UTF-8 bytes back as text. CAST(blob AS VARCHAR) would NOT be equivalent:
    // it renders non-ASCII bytes as literal \xHH escapes (see ConditionsService for the same
    // lesson). typeof-guarded so a VARCHAR column (older fixture warehouses) still reads as-is.
    final String sql =
        "SELECT CASE WHEN typeof(bpmn_xml) = 'BLOB' THEN decode(CAST(bpmn_xml AS BLOB))"
            + " ELSE CAST(bpmn_xml AS VARCHAR) END FROM "
            + SqlText.identifier(PROCESS_DEFINITIONS)
            + " WHERE process_id = "
            + SqlText.literal(processId)
            + " AND version = "
            + version
            + " ORDER BY deployed_at DESC LIMIT 1";
    try {
      final QueryResult result = queryService.execute(sql);
      if (result.rows().isEmpty()) {
        throw new NoSuchElementException(
            "No process definition found for " + processId + " v" + version);
      }
      final String bpmnXml = (String) result.rows().get(0).get(0);
      return new ProcessDefinitionResult(processId, version, bpmnXml, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Process-definition query failed: " + e.getMessage(), e);
    }
  }

  /**
   * Every distinct {@code process_id} in {@code process_definitions} with its deployed versions,
   * ordered by {@code processId} then {@code version} -- deliberately excludes {@code bpmn_xml}
   * (this is a picker listing, not a definition lookup; that BLOB is large and irrelevant here). A
   * missing view degrades to an empty list, never an error -- see the class javadoc.
   */
  public ProcessDefinitionsListResult list() {
    if (!viewRegistry.ensureAvailable(PROCESS_DEFINITIONS)) {
      return new ProcessDefinitionsListResult(List.of(), List.of());
    }
    final String sql =
        "SELECT process_id, version, deployed_at FROM "
            + SqlText.identifier(PROCESS_DEFINITIONS)
            + " ORDER BY process_id, version";
    try {
      final QueryResult result = queryService.execute(sql);
      final List<ProcessDefinitionSummary> definitions = new ArrayList<>();
      String currentProcessId = null;
      List<ProcessDefinitionVersion> currentVersions = null;
      for (final List<Object> row : result.rows()) {
        final String processId = (String) row.get(0);
        final int version = ((Number) row.get(1)).intValue();
        final String deployedAt = SqlText.toIsoString(row.get(2));
        if (!processId.equals(currentProcessId)) {
          if (currentProcessId != null) {
            definitions.add(summarize(currentProcessId, currentVersions));
          }
          currentProcessId = processId;
          currentVersions = new ArrayList<>();
        }
        currentVersions.add(new ProcessDefinitionVersion(version, deployedAt));
      }
      if (currentProcessId != null) {
        definitions.add(summarize(currentProcessId, currentVersions));
      }
      return new ProcessDefinitionsListResult(definitions, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException(
          "Process-definitions list query failed: " + e.getMessage(), e);
    }
  }

  private static ProcessDefinitionSummary summarize(
      final String processId, final List<ProcessDefinitionVersion> versions) {
    int latestVersion = versions.get(0).version();
    for (final ProcessDefinitionVersion version : versions) {
      latestVersion = Math.max(latestVersion, version.version());
    }
    return new ProcessDefinitionSummary(processId, latestVersion, versions);
  }

  /** {@code GET /api/definitions} response. */
  public record ProcessDefinitionResult(
      String processId, int version, String bpmnXml, List<String> sql) {}

  /** One {@code (version, deployedAt)} entry in a {@link ProcessDefinitionSummary}. */
  public record ProcessDefinitionVersion(int version, String deployedAt) {}

  /** One process id's summary in a {@code GET /api/definitions/list} response. */
  public record ProcessDefinitionSummary(
      String processId, int latestVersion, List<ProcessDefinitionVersion> versions) {}

  /** {@code GET /api/definitions/list} response. */
  public record ProcessDefinitionsListResult(
      List<ProcessDefinitionSummary> definitions, List<String> sql) {}
}
