/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
