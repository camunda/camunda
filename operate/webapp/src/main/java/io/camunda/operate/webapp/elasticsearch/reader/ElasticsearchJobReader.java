/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.JobReader;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import java.io.IOException;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchJobReader extends AbstractReader implements JobReader {

  private final JobTemplate jobTemplate;

  public ElasticsearchJobReader(final JobTemplate jobTemplate) {
    this.jobTemplate = jobTemplate;
  }

  @Override
  public Optional<JobEntity> getJobByFlowNodeInstanceId(final String flowNodeInstanceId) {
    final var query =
        ElasticsearchUtil.termsQuery(JobTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(tenantAwareQuery);

    final var searchRequest =
        new SearchRequest.Builder()
            .index(whereToSearch(jobTemplate, ALL))
            .query(constantScoreQuery)
            .sort(s -> s.field(f -> f.field(JobTemplate.JOB_KEY).order(SortOrder.Asc)))
            .size(1)
            .build();

    try {
      final var response = es8client.search(searchRequest, JobEntity.class);
      return response.hits().hits().stream().findFirst().map(Hit::source);
    } catch (final IOException e) {
      throw new OperateRuntimeException("Error reading job from Elasticsearch", e);
    }
  }
}
