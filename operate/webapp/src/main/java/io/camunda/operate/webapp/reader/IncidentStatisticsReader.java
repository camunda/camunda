/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import java.util.Set;

public interface IncidentStatisticsReader {
  String PROCESS_KEYS = "processDefinitionKeys";
  Aggregation COUNT_PROCESS_KEYS =
      new Aggregation.Builder()
          .terms(t -> t.field(PROCESS_KEY).size(ElasticsearchUtil.TERMS_AGG_SIZE))
          .build();

  Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics();

  Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError();
}
