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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;
import org.camunda.operate.es.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private List<ActivityInstanceForDetailViewEntity> getAllActivityInstances(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ActivityInstanceTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);

    //request incidents
    final SearchRequestBuilder requestBuilder =
      esClient.prepareSearch(activityInstanceTemplate.getAlias())
        .setQuery(constantScoreQuery(workflowInstanceIdQ))
        .addSort(ActivityInstanceTemplate.POSITION, SortOrder.ASC);
    return scroll(requestBuilder);
  }


  protected List<ActivityInstanceForDetailViewEntity> scroll(SearchRequestBuilder builder) {
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse response = builder
      .setScroll(keepAlive)
      .get();

    List<ActivityInstanceForDetailViewEntity> result = new ArrayList<>();

    do {

      SearchHits hits = response.getHits();
      String scrollId = response.getScrollId();

      result.addAll(ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, ActivityInstanceForDetailViewEntity.class));

      response = esClient
        .prepareSearchScroll(scrollId)
        .setScroll(keepAlive)
        .get();

    } while (response.getHits().getHits().length != 0);

    return result;
  }

}
