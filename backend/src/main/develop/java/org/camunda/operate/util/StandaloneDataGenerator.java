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

import java.util.Arrays;
import org.camunda.operate.property.OperateProperties;
import io.zeebe.client.ZeebeClient;

public class StandaloneDataGenerator {

  public static void main(String... args) {
    ZeebeDemoDataGenerator zeebeDemoDataGenerator = new ZeebeDemoDataGenerator();
    final ZeebeClient zeebeClient = createZeebeClient(args[0]);
    final String topicName = args.length == 1 ? "new-topic" : args[1];
    zeebeDemoDataGenerator.setClient(zeebeClient);
    zeebeDemoDataGenerator.setZeebeUtil(createZeebeUtil(zeebeClient));
    zeebeDemoDataGenerator.setOperateProperties(createOperateProperties(topicName));
    zeebeDemoDataGenerator.createZeebeData(true);
    zeebeClient.close();
  }

  private static OperateProperties createOperateProperties(String topicName) {
    OperateProperties operateProperties = new OperateProperties();
    operateProperties.getZeebe().setTopics(Arrays.asList(topicName));
    return operateProperties;
  }

  private static ZeebeUtil createZeebeUtil(ZeebeClient zeebeClient) {
    ZeebeUtil zeebeUtil = new ZeebeUtil();
    zeebeUtil.setClient(zeebeClient);
    return zeebeUtil;
  }

  private static ZeebeClient createZeebeClient(String brokerContactPoint) {
    return ZeebeClient
      .newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .build();

  }

}
