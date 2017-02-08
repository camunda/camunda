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
package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public class UsersType implements TypeMappingCreator{

  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionType.class);

  @Autowired
  ConfigurationService configurationService;

  @Override
  public String getType() {
    return configurationService.getElasticSearchUsersType();
  }

  @Override
  public String getSource() {
    String source = null;
    try {
      XContentBuilder content = jsonBuilder()
        .startObject()
          .startObject("properties")
            .startObject("username")
              .field("type", "keyword")
            .endObject()
            .startObject("password")
              .field("type", "keyword")
            .endObject()
          .endObject()
        .endObject();
      source = content.string();
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + configurationService.getOptimizeIndex() +
        "' , type '" + getType() + "'!";
      logger.error(message, e);
    };
    return source;
  }
}
