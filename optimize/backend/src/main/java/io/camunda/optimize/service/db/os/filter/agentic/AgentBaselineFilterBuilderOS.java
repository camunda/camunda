/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.filter.agentic;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.json;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.terms;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;

import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

/**
 * Builds the baseline OS filter list applied to all Agentic Control Plane repository queries.
 * Tenant filtering is a security requirement — never omit it.
 */
@NullMarked
public final class AgentBaselineFilterBuilderOS {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT).withZone(ZoneOffset.UTC);

  private AgentBaselineFilterBuilderOS() {}

  public static List<Query> build(final AgentQueryParams params) {
    final List<Query> filters = new ArrayList<>();

    filters.add(term(STATE, COMPLETED_STATE));

    final String from =
        FORMATTER.format(OffsetDateTime.ofInstant(params.startDateFrom(), ZoneOffset.UTC));
    final String to =
        FORMATTER.format(OffsetDateTime.ofInstant(params.startDateTo(), ZoneOffset.UTC));
    filters.add(
        new RangeQuery.Builder()
            .field(START_DATE)
            .gte(json(from))
            .lte(json(to))
            .format(OPTIMIZE_DATE_FORMAT)
            .build()
            .toQuery());

    filters.add(terms(TENANT_ID, params.tenantIds()));

    filters.add(
        nested(
            AGENT_INSTANCES,
            exists(AGENT_INSTANCES + "." + AGENT_INSTANCE_ID),
            ChildScoreMode.None));

    if (params.processDefinitionKey() != null) {
      filters.add(term(PROCESS_DEFINITION_KEY, params.processDefinitionKey()));
    }

    return filters;
  }
}
