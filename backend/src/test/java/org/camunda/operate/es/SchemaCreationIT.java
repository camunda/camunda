/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.es;

import java.util.concurrent.ExecutionException;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.script.mustache.SearchTemplateRequestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

public class SchemaCreationIT extends OperateIntegrationTest {

  @Autowired
  private TransportClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private WorkflowInstanceTemplate workflowInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testIndexCreation() throws ExecutionException, InterruptedException {
    assertIndexAndAlias(operateProperties.getElasticsearch().getWorkflowIndexName(), operateProperties.getElasticsearch().getWorkflowAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getWorkflowInstanceIndexName(), workflowInstanceTemplate.getAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getEventIndexName(), eventTemplate.getAlias());
    assertIndexAndAlias(operateProperties.getElasticsearch().getImportPositionIndexName(), operateProperties.getElasticsearch().getImportPositionAlias());

    assertTemplateOrder(workflowInstanceTemplate.getTemplateName(), 30);
    assertTemplateOrder(eventTemplate.getTemplateName(), 30);

    //assert schema creation won't be performed for the second time
    assertThat(elasticsearchSchemaManager.initializeSchema()).isFalse();
  }

  private void assertTemplateOrder(String templateName, int templateOrder) {
    final GetIndexTemplatesResponse getIndexTemplatesResponse =
      esClient.admin().indices()
        .prepareGetTemplates(templateName)
        .get();

    assertThat(getIndexTemplatesResponse.getIndexTemplates()).hasSize(1);
    assertThat(getIndexTemplatesResponse.getIndexTemplates().iterator().next().getOrder()).isEqualTo(templateOrder);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws InterruptedException, ExecutionException {
    final GetIndexResponse getIndexResponse =
      esClient.admin().indices()
        .prepareGetIndex().addIndices(indexName
      ).execute().get();

    assertThat(getIndexResponse.getAliases()).hasSize(1);
    assertThat(getIndexResponse.getAliases().valuesIt().next().get(0).getAlias()).isEqualTo(aliasName);
  }

}
