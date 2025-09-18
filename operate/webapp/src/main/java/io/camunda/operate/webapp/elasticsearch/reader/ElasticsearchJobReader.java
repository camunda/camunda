/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.JobReader;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.entities.JobEntity;
import java.io.IOException;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchJobReader extends AbstractReader implements JobReader {

  private final JobTemplate jobTemplate;
  private final TenantAwareElasticsearchClient tenantAwareClient;

  public ElasticsearchJobReader(
      final JobTemplate jobTemplate, final TenantAwareElasticsearchClient tenantAwareClient) {
    this.jobTemplate = jobTemplate;
    this.tenantAwareClient = tenantAwareClient;
  }

  @Override
  public Optional<JobEntity> getJobByFlowNodeInstanceId(final String flowNodeInstanceId) {
    final QueryBuilder query =
        constantScoreQuery(termQuery(JobTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(jobTemplate)
            .source(new SearchSourceBuilder().query(query).sort(JobTemplate.JOB_KEY));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      final var hits = response.getHits();
      final var totalHits =
          (hits != null && hits.getTotalHits() != null) ? hits.getTotalHits().value : 0L;
      if (totalHits >= 1) {
        // take the first job found
        final JobEntity job =
            ElasticsearchUtil.fromSearchHit(
                hits.getHits()[0].getSourceAsString(), objectMapper, JobEntity.class);
        return Optional.of(job);
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException("Error reading job from Elasticsearch", e);
    }
  }
}
