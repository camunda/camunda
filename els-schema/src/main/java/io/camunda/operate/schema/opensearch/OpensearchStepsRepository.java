/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.MigrationException;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.migration.StepsRepository;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchStepsRepository implements StepsRepository {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/opensearch/change";

  @Autowired
  private OpenSearchClient openSearchClient;

  @Qualifier("operateObjectMapper")
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private MigrationRepositoryIndex migrationRepositoryIndex;

  /**
   * Updates Steps in index by comparing steps in json format with documents from index.
   * If there are any new steps then they will be saved in index.
   */
  @Override
  public void updateSteps() throws IOException, MigrationException {
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        logger.info("Add new step {} to repository.", step);
        save(step);
      }
    }
    openSearchClient.indices().refresh(r -> r.index(getName()));
  }

  private List<Step> readStepsFromClasspath() throws IOException {
    List<Step> steps = new ArrayList<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      Resource[] resources =
          resolver.getResources(
              OpensearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER
                  + "/*"
                  + STEP_FILE_EXTENSION);

      for (Resource resource : resources) {
        logger.info("Read step {} ", resource.getFilename());
        steps.add(readStepFromFile(resource.getInputStream()));
      }
      steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);
      return steps;
    } catch (FileNotFoundException ex) {
      //ignore
      logger.warn("Directory with migration steps was not found: " + ex.getMessage());
    }
    return steps;
  }

  private Step readStepFromFile(final InputStream is) throws IOException {
    return objectMapper.readValue(is, Step.class);
  }

  /**
   * Returns the of repository. It is used as index name for elasticsearch
   */
  @Override
  public String getName() {
    return migrationRepositoryIndex.getFullQualifiedName();
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step) throws MigrationException, IOException {
    var result = openSearchClient.index(i -> i.index(getName()).id(idFromStep(step)).document(step)).result();
    if (result.equals(Result.Created) || result.equals(Result.Updated)) {
      logger.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  /**
   * Returns all stored steps in repository index
   */
  @Override
  public List<Step> findAll() {
    logger.debug("Find all steps from Opensearch at {}:{} ", operateProperties.getOpensearch().getHost(), operateProperties.getOpensearch().getPort());
    try {
      SearchResponse<Step> searchResponse = openSearchClient.search(s -> s.index(getName()), Step.class);
      return searchResponse.documents();
    } catch (IOException e) {
      throw new OperateRuntimeException("Could not get all steps from Opensearch repository", e);
    }
  }
  /**
   * Returns all steps for an index that are not applied yet.
   */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    logger.debug("Find 'not applied steps' for index {} from Opensearch at {}:{} ", indexName,
        operateProperties.getOpensearch().getHost(), operateProperties.getOpensearch().getPort());
    try {
      SearchResponse<Step> searchResponse = openSearchClient.search(s -> s
          .index(getName())
          .query(q -> q.bool(b -> b
                  .must(m -> m.term( t -> t.field(Step.INDEX_NAME + ".keyword").value(v -> v.stringValue(indexName))))
                  .must(m -> m.term(t ->  t.field(Step.APPLIED).value(v -> v.booleanValue(false)))))), Step.class);
      return searchResponse.documents();
    } catch (IOException e) {
      throw new OperateRuntimeException("Could not get steps from Opensearch repository", e);
    }
  }

}
