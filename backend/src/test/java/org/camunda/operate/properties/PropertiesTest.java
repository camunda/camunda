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
package org.camunda.operate.properties;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, OperateProperties.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test-properties")
public class PropertiesTest {

  @Autowired
  private OperateProperties operateProperties;

  @Test
  public void testProperties() {
    assertThat(operateProperties.isStartLoadingDataOnStartup()).isFalse();
    assertThat(operateProperties.getBatchOperationMaxSize()).isEqualTo(500);
    assertThat(operateProperties.getElasticsearch().getClusterName()).isEqualTo("clusterName");
    assertThat(operateProperties.getElasticsearch().getHost()).isEqualTo("someHost");
    assertThat(operateProperties.getElasticsearch().getPort()).isEqualTo(12345);
    assertThat(operateProperties.getElasticsearch().getDateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(operateProperties.getElasticsearch().getBatchSize()).isEqualTo(111);
    assertThat(operateProperties.getElasticsearch().getImportPositionIndexName()).isEqualTo("importPositionI");
    assertThat(operateProperties.getElasticsearch().getEventIndexName()).isEqualTo("eventI");
    assertThat(operateProperties.getElasticsearch().getWorkflowInstanceIndexName()).isEqualTo("workflowInstanceI");
    assertThat(operateProperties.getElasticsearch().getWorkflowIndexName()).isEqualTo("workflowI");
    assertThat(operateProperties.getElasticsearch().getImportPositionAlias()).isEqualTo("importPositionA");
    assertThat(operateProperties.getElasticsearch().getWorkflowAlias()).isEqualTo("workflowA");
    assertThat(operateProperties.getElasticsearch().getTemplateOrder()).isEqualTo(50);
    assertThat(operateProperties.getZeebeElasticsearch().getClusterName()).isEqualTo("zeebeElasticClusterName");
    assertThat(operateProperties.getZeebeElasticsearch().getHost()).isEqualTo("someOtherHost");
    assertThat(operateProperties.getZeebeElasticsearch().getPort()).isEqualTo(54321);
    assertThat(operateProperties.getZeebeElasticsearch().getDateFormat()).isEqualTo("dd-MM-yyyy");
    assertThat(operateProperties.getZeebeElasticsearch().getBatchSize()).isEqualTo(222);
    assertThat(operateProperties.getZeebeElasticsearch().getPrefix()).isEqualTo("somePrefix");
    assertThat(operateProperties.getZeebe().getBrokerContactPoint()).isEqualTo("someZeebeHost:999");
    assertThat(operateProperties.getOperationExecutor().getBatchSize()).isEqualTo(555);
    assertThat(operateProperties.getOperationExecutor().getWorkerId()).isEqualTo("someWorker");
    assertThat(operateProperties.getOperationExecutor().getLockTimeout()).isEqualTo(15000);
    assertThat(operateProperties.getOperationExecutor().isExecutorEnabled()).isFalse();
  }

}
