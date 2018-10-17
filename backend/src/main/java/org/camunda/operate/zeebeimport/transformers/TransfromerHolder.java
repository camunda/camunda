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
package org.camunda.operate.zeebeimport.transformers;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.zeebe.protocol.clientapi.ValueType;

@Configuration
public class TransfromerHolder {

  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private WorkflowInstanceRecordTransformer workflowInstanceRecordTransformer;
  @Autowired
  private DeploymentEventTransformer deploymentEventTransformer;
  @Autowired
  private JobEventTransformer jobEventTransformer;
  @Autowired
  private IncidentEventTransformer incidentEventTransformer;

  @Bean
  public Map<ValueType, AbstractRecordTransformer> getZeebeRecordTransformerMapping() {
    Map<ValueType, AbstractRecordTransformer> map = new HashMap<>();
    map.put(ValueType.WORKFLOW_INSTANCE, workflowInstanceRecordTransformer);
    map.put(ValueType.DEPLOYMENT, deploymentEventTransformer);
    map.put(ValueType.JOB, jobEventTransformer);
    map.put(ValueType.INCIDENT, incidentEventTransformer);
    return map;
  }

}
