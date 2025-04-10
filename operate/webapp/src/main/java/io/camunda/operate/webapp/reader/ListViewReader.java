/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;

public interface ListViewReader {
  ListViewResponseDto queryProcessInstances(ListViewRequestDto processInstanceRequest);

  List<ProcessInstanceForListViewEntity> queryListView(
      ListViewRequestDto processInstanceRequest, ListViewResponseDto result);

  Tuple<String, String> getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
      final String flowNodeInstanceId);
}
