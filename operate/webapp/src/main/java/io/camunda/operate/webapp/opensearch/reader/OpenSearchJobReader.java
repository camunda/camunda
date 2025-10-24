/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.reader.JobReader;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import java.util.Optional;
import org.opensearch.client.opensearch.core.search.SearchResult;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpenSearchJobReader extends OpensearchAbstractReader implements JobReader {

  private final JobTemplate jobTemplate;

  public OpenSearchJobReader(final JobTemplate jobTemplate) {
    this.jobTemplate = jobTemplate;
  }

  @Override
  public Optional<JobEntity> getJobByFlowNodeInstanceId(final String flowNodeInstanceId) {
    final var query = term(JobTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceId);
    final var searchRequestBuilder =
        searchRequestBuilder(jobTemplate.getAlias()).query(query).size(1);
    final SearchResult<JobEntity> searchResult =
        richOpenSearchClient.doc().fixedSearch(searchRequestBuilder.build(), JobEntity.class);
    if (searchResult.hits().total() != null
        && searchResult.hits().total().value() > 0
        && !searchResult.hits().hits().isEmpty()) {
      return Optional.ofNullable(searchResult.hits().hits().getFirst().source());
    }
    return Optional.empty();
  }
}
