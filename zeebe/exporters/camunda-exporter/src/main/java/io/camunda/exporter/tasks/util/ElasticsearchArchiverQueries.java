/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.CountRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;

public class ElasticsearchArchiverQueries {

  private ElasticsearchArchiverQueries() {}

  public static Query finishedProcessInstancesQuery(
      final String archivingTimePoint, final int partitionId) {
    final var endDateQ =
        QueryBuilders.range(
            q -> q.date(d -> d.field(ListViewTemplate.END_DATE).lte(archivingTimePoint)));
    final var isProcessInstanceQ =
        QueryBuilders.term(
            q ->
                q.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    final var partitionQ =
        QueryBuilders.term(q -> q.field(ListViewTemplate.PARTITION_ID).value(partitionId));
    return QueryBuilders.bool(
        q -> q.filter(endDateQ).filter(isProcessInstanceQ).filter(partitionQ));
  }

  public static CountRequest createFinishedInstancesCountRequest(
      final String processInstanceIndexName,
      final String archivingTimePoint,
      final int partitionId) {
    return CountRequest.of(
        cr ->
            cr.index(processInstanceIndexName)
                .query(finishedProcessInstancesQuery(archivingTimePoint, partitionId)));
  }
}
