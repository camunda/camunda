/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.elasticsearch.action.search.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {
  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(ListViewQueryDto query) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(SearchRequest searchRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SearchRequest createQuery(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {
    throw new UnsupportedOperationException();
  }
}
