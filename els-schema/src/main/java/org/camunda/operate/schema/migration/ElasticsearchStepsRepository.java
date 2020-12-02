/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.MigrationRepositoryIndex;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Saves and retrieves Steps from Elasticsearch index.<br>
 * After creation it updates the repository index by looking in classpath folder for new steps.<br>
 *
 */
@Component
public class ElasticsearchStepsRepository implements StepsRepository {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/change";

  @Autowired
  private RestHighLevelClient esClient;

  @Qualifier("operateObjectMapper")
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperateProperties operateProperties;

  /**
   * Updates Steps in index by comparing steps in json format with documents from index.
   * If there are any new steps then they will be saved in index.
   */
  @PostConstruct
  public void updateStepsInRepository(){
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        logger.info("Add new step {} to repository.", step);
        save(step);
      }
    }
  }

  private List<Step> readStepsFromClasspath(){
    List<Step> steps = new ArrayList<>();
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources(ElasticsearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER + "/*" + STEP_FILE_EXTENSION);

      for(Resource resource: resources) {
        logger.info("Read step {} ", resource.getFilename());
        steps.add(readStepFromFile(resource.getFilename(), resource.getInputStream()));
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(String.format("Could not get steps folder from classpath %s", ElasticsearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER), e);
    }

    steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);
    return steps;
  }

  private Step readStepFromFile(final String name,final InputStream is) {
    try {
      return objectMapper.readValue(is, Step.class);
    } catch (IOException e) {
      throw new OperateRuntimeException(String.format("Error in reading step from file %s",name), e);
    }
  }

  /**
   * Returns the of repository. It is used as index name for elasticsearch
   */
  @Override
  public String getName() {
    return operateProperties.getElasticsearch().getIndexPrefix() + "-" + MigrationRepositoryIndex.INDEX_NAME;
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step){
    final IndexResponse response;
    try {
      response = esClient
          .index(new IndexRequest(getName(), ElasticsearchUtil.ES_INDEX_TYPE, idFromStep(step))
              .source(objectMapper.writeValueAsString(step), XContentType.JSON), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OperateRuntimeException(String.format("Error in save step %s", step), e);
    }
    Result result = response.getResult();
    if (result.equals(Result.CREATED) || result.equals(Result.UPDATED)) {
      logger.info("Step {}  saved.", step);
    } else {
      throw new OperateRuntimeException(String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  protected List<Step> findBy(final Optional<QueryBuilder> query){
    try {
      final SearchSourceBuilder searchSpec = new SearchSourceBuilder()
          .sort(Step.VERSION+ ".keyword", SortOrder.ASC);
      query.ifPresent(searchSpec::query);
      SearchRequest request = new SearchRequest(getName())
          .source(searchSpec)
          .indicesOptions(IndicesOptions.lenientExpandOpen());
      return ElasticsearchUtil.scroll(request, Step.class, objectMapper, esClient);
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed finding migration steps.", e);
    }
  }

  /**
   * Returns all stored steps in repository index
   */
  @Override
  public List<Step> findAll() {
    logger.debug("Find all steps from Elasticsearch at {}:{} ", operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort());

    return findBy(Optional.empty());
  }
  /**
   * Returns all steps for an index that are not applied yet.
   */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    logger.debug("Find 'not applied steps' for index {} from Elasticsearch at {}:{} ", indexName,
        operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort());

    return findBy(Optional.ofNullable(
        joinWithAnd(
            termQuery(Step.INDEX_NAME + ".keyword", indexName),
            termQuery(Step.APPLIED, false))));
  }

}
