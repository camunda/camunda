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
package org.camunda.operate.es.reader;

import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.rest.dto.detailview.VariablesRequestDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class DetailViewReader {


  private static final Logger logger = LoggerFactory.getLogger(DetailViewReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;

  @Autowired
  private VariableTemplate variableTemplate;

  public List<VariableEntity> getVariables(VariablesRequestDto variableRequest) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(VariableTemplate.WORKFLOW_INSTANCE_ID, variableRequest.getWorkflowInstanceId());
    final TermQueryBuilder scopeIdQ = termQuery(VariableTemplate.SCOPE_ID, variableRequest.getScopeId());

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceIdQ, scopeIdQ));

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(variableTemplate.getAlias())
        .setQuery(query)
        .addSort(VariableTemplate.NAME, SortOrder.ASC);
    return scroll(requestBuilder, VariableEntity.class);

  }

  public ActivityInstanceTreeDto getActivityInstanceTree(ActivityInstanceTreeRequestDto requestDto) {

    List<ActivityInstanceForDetailViewEntity> activityInstances = getAllActivityInstances(requestDto.getWorkflowInstanceId());

    final Map<String, DetailViewActivityInstanceDto> nodes = DetailViewActivityInstanceDto.createMapFrom(activityInstances);

    ActivityInstanceTreeDto tree = new ActivityInstanceTreeDto();

    for (DetailViewActivityInstanceDto node: nodes.values()) {
      if (node.getParentId() != null) {
        if (node.getParentId().equals(requestDto.getWorkflowInstanceId())) {
          tree.getChildren().add(node);
        } else {
          nodes.get(node.getParentId()).getChildren().add(node);
        }
        if (node.getState().equals(ActivityState.INCIDENT)) {
          propagateState(node, nodes);
        }
      }
    }

    return tree;
  }

  private void propagateState(DetailViewActivityInstanceDto currentNode, Map<String, DetailViewActivityInstanceDto> allNodes) {
    if (currentNode.getParentId() != null) {
      final DetailViewActivityInstanceDto parent = allNodes.get(currentNode.getParentId());
      if (parent != null) {
        parent.setState(ActivityState.INCIDENT);
        propagateState(parent, allNodes);
      }
    }
  }

  public List<ActivityInstanceForDetailViewEntity> getAllActivityInstances(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ActivityInstanceTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(activityInstanceTemplate.getAlias())
        .setQuery(constantScoreQuery(workflowInstanceIdQ))
        .addSort(ActivityInstanceTemplate.POSITION, SortOrder.ASC);
    return scroll(requestBuilder, ActivityInstanceForDetailViewEntity.class);
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequestBuilder builder, Class<T> clazz) {
    return ElasticsearchUtil.scroll(builder, clazz, objectMapper, esClient);
  }

}
