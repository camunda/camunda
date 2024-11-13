/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration.os;

import static io.camunda.tasklist.util.OpenSearchUtil.joinWithAnd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.MigrationRepositoryIndex;
import io.camunda.tasklist.schema.v86.migration.Step;
import io.camunda.tasklist.schema.v86.migration.StepsRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchStepsRepository implements StepsRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/os/change";

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Qualifier("tasklistObjectMapper")
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private MigrationRepositoryIndex migrationRepositoryIndex;

  /**
   * Updates Steps in index by comparing steps in json format with documents from index. If there
   * are any new steps then they will be saved in index.
   */
  @Override
  public void updateSteps() throws IOException, MigrationException {
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        LOGGER.info("Add new step {} to repository.", step);
        save(step);
      }
    }
    retryOpenSearchClient.refresh(migrationRepositoryIndex.getFullQualifiedName());
  }

  private List<Step> readStepsFromClasspath() throws IOException {
    final List<Step> steps = new ArrayList<>();

    final List<Resource> resources =
        getResourcesFor(
            OpenSearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER + "/*" + STEP_FILE_EXTENSION);
    for (Resource resource : resources) {
      LOGGER.info("Read step {} ", resource.getFilename());
      steps.add(readStepFromFile(resource.getInputStream()));
    }
    steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);

    return steps;
  }

  private List<Resource> getResourcesFor(final String pattern) {
    final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      return Arrays.asList(resolver.getResources(pattern));
    } catch (IOException e) {
      LOGGER.info("No resources found for {} ", pattern);
      return List.of();
    }
  }

  private Step readStepFromFile(final InputStream is) throws IOException {
    return objectMapper.readValue(is, Step.class);
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step) throws MigrationException, IOException {
    final boolean createdOrUpdated =
        retryOpenSearchClient.createOrUpdateDocument(
            migrationRepositoryIndex.getFullQualifiedName(),
            idFromStep(step),
            CommonUtils.getJsonObjectFromEntity(step));
    if (createdOrUpdated) {
      LOGGER.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(
          String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  protected List<Step> findBy(final Optional<Query> query) {
    final SearchRequest request =
        SearchRequest.of(
            sr -> {
              query.ifPresent(sr::query);
              return sr.index(migrationRepositoryIndex.getFullQualifiedName())
                  .sort(
                      sort ->
                          sort.field(
                              f ->
                                  f.field(Step.VERSION + ".keyword")
                                      .order(
                                          org.opensearch.client.opensearch._types.SortOrder.Asc)))
                  .allowNoIndices(true)
                  .ignoreUnavailable(true)
                  .expandWildcards(ExpandWildcard.Open)
                  .scroll(s -> s.time(RetryOpenSearchClient.SCROLL_KEEP_ALIVE_MS));
            });
    return retryOpenSearchClient.searchWithScroll(request, Step.class, objectMapper);
  }

  /** Returns all stored steps in repository index */
  @Override
  public List<Step> findAll() {
    LOGGER.debug(
        "Find all steps from OpenSearch at {}:{} ",
        tasklistProperties.getOpenSearch().getHost(),
        tasklistProperties.getOpenSearch().getPort());

    return findBy(Optional.empty());
  }

  /** Returns all steps for an index that are not applied yet. */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    LOGGER.debug(
        "Find 'not applied steps' for index {} from OpenSearch at {}:{} ",
        indexName,
        tasklistProperties.getOpenSearch().getHost(),
        tasklistProperties.getOpenSearch().getPort());

    return findBy(
        Optional.of(
            joinWithAnd(
                new TermQuery.Builder()
                    .field(Step.INDEX_NAME + ".keyword")
                    .value(FieldValue.of(indexName)),
                new TermQuery.Builder().field(Step.APPLIED).value(FieldValue.of(false)))));
  }
}
