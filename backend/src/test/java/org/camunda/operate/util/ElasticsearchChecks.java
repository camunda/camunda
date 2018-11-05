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
package org.camunda.operate.util;

import java.util.function.Predicate;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.client.transport.TransportClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

@Configuration
public class ElasticsearchChecks {

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowReader workflowReader;

  @Bean(name = "workflowIsDeployedCheck")
  public Predicate<Object[]> getWorkflowIsDeployedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(String.class);
      String workflowId = (String)objects[0];
      try {
        final WorkflowEntity workflow = workflowReader.getWorkflow(workflowId);
        return workflow != null;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

}
