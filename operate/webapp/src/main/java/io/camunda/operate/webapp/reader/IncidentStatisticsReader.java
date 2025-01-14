/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

public interface IncidentStatisticsReader {
  String PROCESS_KEYS = "processDefinitionKeys";
  AggregationBuilder COUNT_PROCESS_KEYS =
      terms(PROCESS_KEYS).field(PROCESS_KEY).size(ElasticsearchUtil.TERMS_AGG_SIZE);
  QueryBuilder INCIDENTS_QUERY =
      joinWithAnd(
          termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          termQuery(STATE, ProcessInstanceState.ACTIVE.toString()),
          termQuery(INCIDENT, true));

  Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics();

  Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError();
}
