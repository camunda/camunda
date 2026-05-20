/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter.agentic;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.agentic.AgentQueryParams;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the baseline ES bool filter applied to all Agentic Control Plane repository queries.
 * Tenant filtering is a security requirement — never omit it.
 */
@NullMarked
public final class AgentBaselineFilterBuilderES {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT).withZone(ZoneOffset.UTC);

  private AgentBaselineFilterBuilderES() {}

  public static BoolQuery.Builder build(final AgentQueryParams params) {
    final BoolQuery.Builder bool = new BoolQuery.Builder();

    bool.filter(f -> f.term(t -> t.field(STATE).value(COMPLETED_STATE)));

    final String from =
        FORMATTER.format(OffsetDateTime.ofInstant(params.startDateFrom(), ZoneOffset.UTC));
    final String to =
        FORMATTER.format(OffsetDateTime.ofInstant(params.startDateTo(), ZoneOffset.UTC));
    bool.filter(
        f ->
            f.range(
                r ->
                    r.date(
                        d -> d.field(START_DATE).gte(from).lte(to).format(OPTIMIZE_DATE_FORMAT))));

    bool.filter(
        f ->
            f.terms(
                t ->
                    t.field(TENANT_ID)
                        .terms(
                            tt ->
                                tt.value(
                                    params.tenantIds().stream().map(FieldValue::of).toList()))));

    bool.filter(
        f ->
            f.nested(
                n ->
                    n.path(AGENT_INSTANCES)
                        .scoreMode(ChildScoreMode.None)
                        .query(
                            q ->
                                q.exists(
                                    e -> e.field(AGENT_INSTANCES + "." + AGENT_INSTANCE_ID)))));

    if (params.processDefinitionKey() != null) {
      bool.filter(
          f -> f.term(t -> t.field(PROCESS_DEFINITION_KEY).value(params.processDefinitionKey())));
    }

    return bool;
  }
}
