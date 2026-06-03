/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.agent;

import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;

/**
 * Painless scripts shared by the Elasticsearch and OpenSearch agent metric interpreters. The script
 * bodies are database-agnostic (they reference only shared {@link ProcessInstanceIndex} field
 * constants), so they live here rather than being duplicated per stack — see {@code
 * AbstractProcessViewRawDataInterpreter} for the same pattern.
 */
public final class AgentMetricScripts {

  /**
   * Total tokens is derived (not a raw Zeebe field): the sum of the input and output token fields.
   * The {@code .empty} guard handles non-agent docs that lack the fields — the index mapping's
   * {@code null_value(0L)} only substitutes explicit nulls, not missing fields, so without the
   * guard the script would throw on such docs.
   */
  public static final String TOTAL_TOKENS =
      "long inputTokens = doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_INPUT_TOKENS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_INPUT_TOKENS
          + "'].value;"
          + "long outputTokens = doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_OUTPUT_TOKENS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_OUTPUT_TOKENS
          + "'].value;"
          + "return inputTokens + outputTokens;";

  private AgentMetricScripts() {}
}
