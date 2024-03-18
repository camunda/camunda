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
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.migration.util.TestConstants.DEFAULT_TENANT_ID;
import static io.camunda.operate.qa.migration.v800.DMNDataGenerator.DECISION_COUNT;
import static io.camunda.operate.qa.migration.v800.DMNDataGenerator.PROCESS_INSTANCE_COUNT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.v800.DMNDataGenerator;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Before;
import org.junit.Test;

public class DMNTest extends AbstractMigrationTest {

  private String bpmnProcessId = DMNDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> processInstanceIds;

  @Before
  public void findProcessInstanceIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (processInstanceIds == null) {
      final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Process instances list
      searchRequest
          .source()
          .query(
              joinWithAnd(
                  termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                  termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
      try {
        processInstanceIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(processInstanceIds).hasSize(PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testDecisionDefinitions() {
    final List<DecisionDefinitionEntity> decisionDefinitions =
        entityReader.getEntitiesFor(decisionTemplate.getAlias(), DecisionDefinitionEntity.class);
    assertThat(decisionDefinitions).hasSize(DECISION_COUNT);
    decisionDefinitions.forEach(
        di -> {
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }

  @Test
  public void testDRD() {
    final List<DecisionRequirementsEntity> decisionRequirements =
        entityReader.getEntitiesFor(
            decisionRequirementsIndex.getAlias(), DecisionRequirementsEntity.class);
    assertThat(decisionRequirements).hasSize(1);
    decisionRequirements.forEach(
        di -> {
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }

  @Test
  public void testDecisionInstances() {
    final SearchRequest searchRequest = new SearchRequest(decisionInstanceTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<DecisionInstanceEntity> decisionInstances =
        entityReader.searchEntitiesFor(searchRequest, DecisionInstanceEntity.class);
    assertThat(decisionInstances).hasSize(PROCESS_INSTANCE_COUNT * DECISION_COUNT);
    decisionInstances.forEach(
        di -> {
          assertThat(di.getEvaluatedInputs())
              .map(DecisionInstanceInputEntity::getValue)
              .doesNotContainNull();
          assertThat(di.getEvaluatedOutputs())
              .map(DecisionInstanceOutputEntity::getValue)
              .doesNotContainNull();
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }
}
