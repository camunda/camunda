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
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.service.es.ProcessDefinitionWriter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class ProcessDefinitionImportService {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportService.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private Client client;

  public void executeImport() {

    List<ProcessDefinitionDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint())
      .path(configurationService.getProcessDefinitionEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionDto>>() {
      });

    try {
      procDefWriter.importProcessDefinitions(entries);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
    this.client = client;
  }
}
