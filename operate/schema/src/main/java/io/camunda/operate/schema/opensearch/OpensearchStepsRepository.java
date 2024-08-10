/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.migration.BaseStepsRepository;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.migration.StepsRepository;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchStepsRepository extends BaseStepsRepository implements StepsRepository {

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/opensearch/change";

  private final RichOpenSearchClient richOpenSearchClient;

  private final ObjectMapper objectMapper;

  private final OperateProperties operateProperties;

  private final MigrationRepositoryIndex migrationRepositoryIndex;

  @Autowired
  public OpensearchStepsRepository(
      final OperateProperties operateProperties,
      final @Qualifier("operateObjectMapper") ObjectMapper objectMapper,
      final RichOpenSearchClient richOpenSearchClient,
      final MigrationRepositoryIndex migrationRepositoryIndex) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
    this.richOpenSearchClient = richOpenSearchClient;
    this.migrationRepositoryIndex = migrationRepositoryIndex;
  }

  private Step readStepFromFile(final InputStream is) throws IOException {
    return objectMapper.readValue(is, Step.class);
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step) throws MigrationException, IOException {
    final var createdOrUpdated =
        richOpenSearchClient
            .doc()
            .indexWithRetries(indexRequestBuilder(getName()).id(idFromStep(step)).document(step));
    if (createdOrUpdated) {
      logger.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(
          String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  /** Returns all stored steps in repository index */
  @Override
  public List<Step> findAll() {
    logger.debug(
        "Find all steps from Opensearch at {} ", operateProperties.getOpensearch().getUrl());
    return richOpenSearchClient.doc().searchValues(searchRequestBuilder(getName()), Step.class);
  }

  /** Returns all steps for an index that are not applied yet. */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    logger.debug(
        "Find 'not applied steps' for index {} from Opensearch at {}",
        indexName,
        operateProperties.getOpensearch().getUrl());
    return richOpenSearchClient
        .doc()
        .searchValues(
            searchRequestBuilder(getName())
                .query(
                    and(term(Step.INDEX_NAME + ".keyword", indexName), term(Step.APPLIED, false))),
            Step.class);
  }

  /** Returns the of repository. It is used as index name for elasticsearch */
  @Override
  public String getName() {
    return migrationRepositoryIndex.getFullQualifiedName();
  }

  @Override
  public void refreshIndex() {
    richOpenSearchClient.index().refresh(getName());
  }

  @Override
  public List<Step> readStepsFromClasspath() throws IOException {
    final List<Step> steps = new ArrayList<>();
    final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      final Resource[] resources =
          resolver.getResources(
              OpensearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER + "/*" + STEP_FILE_EXTENSION);

      for (Resource resource : resources) {
        logger.info("Read step {} ", resource.getFilename());
        steps.add(readStepFromFile(resource.getInputStream()));
      }
      steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);
      return steps;
    } catch (FileNotFoundException ex) {
      // ignore
      logger.warn(
          String.format("Directory with migration steps was not found: %s", ex.getMessage()));
    }
    return steps;
  }
}
