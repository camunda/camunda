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
package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionReader {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  public List<ProcessDefinitionDto> getProcessDefinitions() {
    QueryBuilder query;
    query = QueryBuilders.matchAllQuery();

    SearchResponse sr = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessDefinitionType())
      .setQuery(query)
      .get();

    int numberOfProcessDefinitions = (int) (int) sr.getHits().totalHits();
    List<ProcessDefinitionDto> list = new ArrayList<>(numberOfProcessDefinitions);
    for (SearchHit hit : sr.getHits().getHits()) {
      String content = hit.getSourceAsString();
      ProcessDefinitionDto processDefinition = null;
      try {
        processDefinition = objectMapper.readValue(content, ProcessDefinitionDto.class);
      } catch (Exception e) {
        logger.error("Error while reading process definition from elastic search!", e);
      }
      list.add(processDefinition);
    }
    return list;
  }

  public String getProcessDefinitionXmls(String processDefinitionId) {
    QueryBuilder query;
    query = QueryBuilders
      .idsQuery(configurationService.getProcessDefinitionXmlType())
      .addIds(processDefinitionId);

    SearchResponse sr = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessDefinitionXmlType())
      .setQuery(query)
      .get();

    String xml = "";
    try {
      SearchHit hit = sr.getHits().getAt(0);
      String content = hit.getSourceAsString();
      ProcessDefinitionXmlDto processDefinitionXml = objectMapper.readValue(content, ProcessDefinitionXmlDto.class);
      xml = processDefinitionXml.getBpmn20Xml();
    } catch (RuntimeException e) {
      logger.error("Error while reading process definition xml from elastic search! " +
        "No process definition with id " + processDefinitionId + "found!", e);
    } catch (Exception e) {
      logger.error(
        "Error while reading process definition xml from elastic search! " +
          "Could not parse process definition xml with id " + processDefinitionId, e);
    }
    return xml;
  }


}
