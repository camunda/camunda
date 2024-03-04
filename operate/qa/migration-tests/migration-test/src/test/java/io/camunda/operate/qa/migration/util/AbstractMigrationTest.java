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
package io.camunda.operate.qa.migration.util;

import static org.junit.Assume.assumeTrue;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(
    classes = {
      TestConfig.class,
      ElasticsearchSchemaManager.class,
      RetryElasticsearchClient.class,
      ElasticsearchTaskStore.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      JacksonConfig.class
    })
@TestPropertySource(locations = "/test.properties")
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractMigrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMigrationTest.class);

  @Autowired protected EntityReader entityReader;

  @Autowired protected ProcessIndex processTemplate;

  @Autowired protected DecisionIndex decisionTemplate;

  @Autowired protected DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired protected ListViewTemplate listViewTemplate;

  @Autowired protected DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired protected EventTemplate eventTemplate;

  @Autowired protected SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired protected FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired protected VariableTemplate variableTemplate;

  @Autowired protected IncidentTemplate incidentTemplate;

  @Autowired protected ImportPositionIndex importPositionIndex;

  @Autowired protected OperationTemplate operationTemplate;

  @Autowired protected PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired protected UserIndex userIndex;

  @Autowired protected MetricIndex metricIndex;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected TestContext testContext;

  protected void assumeThatProcessIsUnderTest(String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }

  @Configuration
  @ComponentScan(
      basePackages = {
        "io.camunda.operate.property",
        "io.camunda.operate.schema.indices",
        "io.camunda.operate.schema.templates",
        "io.camunda.operate.qa.migration",
        "io.camunda.operate.util.rest"
      },
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class SpringConfig {}
}
