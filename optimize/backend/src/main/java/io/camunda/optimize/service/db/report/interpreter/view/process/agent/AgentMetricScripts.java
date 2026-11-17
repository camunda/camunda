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
   * Returns the pre-computed {@code agentTotalTokens} value, or {@code null} when the instance has
   * no agent activity (null or empty {@code agentInstances} array).
   *
   * <p>The array is read from {@code _source} because nested fields are not accessible via doc
   * values in aggregation scripts. Instances without agent activity are excluded from
   * avg/percentile aggregations so they do not skew the median. Instances with agent activity but
   * zero tokens legitimately contribute 0 and are included.
   */
  public static final String TOTAL_TOKENS =
      "def instances = params._source['"
          + ProcessInstanceIndex.AGENT_INSTANCES
          + "'];"
          + "if (instances == null || instances.size() == 0) return null;"
          + "return doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_TOKENS
          + "'].empty ? 0L : doc['"
          + ProcessInstanceIndex.AGENT_TOTAL_TOKENS
          + "'].value;";

  private AgentMetricScripts() {}
}
