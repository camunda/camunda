/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;

public interface ListViewReader {
    ListViewResponseDto queryProcessInstances(ListViewRequestDto processInstanceRequest);

    List<ProcessInstanceForListViewEntity> queryListView(
            ListViewRequestDto processInstanceRequest, ListViewResponseDto result);

    ConstantScoreQueryBuilder createProcessInstancesQuery(ListViewQueryDto query);

    QueryBuilder createQueryFragment(ListViewQueryDto query);

    QueryBuilder createQueryFragment(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType);
}
