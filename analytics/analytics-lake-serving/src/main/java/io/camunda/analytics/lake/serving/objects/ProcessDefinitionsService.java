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
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Backs {@code GET /api/definitions}: looks up one deployed process definition's BPMN 2.0 XML from
 * the {@code process_definitions} dictionary table (a lake writer table this module only reads --
 * it never deploys or modifies definitions).
 *
 * <h2>Open/closed (read-time degradation)</h2>
 *
 * <p>{@code process_definitions} may not exist yet in an older warehouse (this table is newer than
 * {@code objects}/{@code object_relations}). Same rule as {@link ObjectsService}: never a 500 for a
 * missing view. Both "view absent" and "no row for this (processId, version)" surface as {@link
 * NoSuchElementException}, which {@code ProcessDefinitionsController} maps to a 404 -- the caller
 * (the object detail page's Diagram tab) treats any error response the same way: hide the tab.
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
    final String sql =
        "SELECT bpmn_xml FROM "
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

  /** {@code GET /api/definitions} response. */
  public record ProcessDefinitionResult(
      String processId, int version, String bpmnXml, List<String> sql) {}
}
