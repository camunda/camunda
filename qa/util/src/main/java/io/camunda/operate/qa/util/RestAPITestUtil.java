/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;

import java.util.function.Consumer;

public class RestAPITestUtil {

  public static ListViewQueryDto createProcessInstanceQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    return query;
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery() {
    return
        createProcessInstanceQuery(q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        });
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllFinishedQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery() {
    return
        createProcessInstanceQuery(q -> {
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        });
  }

  public static ListViewQueryDto createGetAllRunningQuery() {
    return
        createProcessInstanceQuery(q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
        });
  }

  public static ListViewRequestDto createProcessInstanceRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewRequestDto request = new ListViewRequestDto();
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    request.setQuery(query);
    return request;
  }

  public static ListViewRequestDto createGetAllProcessInstancesRequest() {
    return
        new ListViewRequestDto(createGetAllProcessInstancesQuery());
  }

  public static DecisionInstanceListQueryDto createDecisionInstanceQuery(Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
    DecisionInstanceListQueryDto query = new DecisionInstanceListQueryDto();
    filtersSupplier.accept(query);
    return query;
  }

  public static DecisionInstanceListRequestDto createDecisionInstanceRequest(Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
    DecisionInstanceListRequestDto request = new DecisionInstanceListRequestDto();
    DecisionInstanceListQueryDto query = new DecisionInstanceListQueryDto();
    filtersSupplier.accept(query);
    request.setQuery(query);
    return request;
  }

  public static DecisionInstanceListQueryDto createGetAllDecisionInstancesQuery() {
    return createDecisionInstanceQuery(q -> q.setFailed(true).setEvaluated(true));
  }

  public static DecisionInstanceListRequestDto createGetAllDecisionInstancesRequest() {
    return
        new DecisionInstanceListRequestDto(createGetAllDecisionInstancesQuery());
  }

  public static DecisionInstanceListRequestDto createGetAllDecisionInstancesRequest(Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
    final DecisionInstanceListQueryDto decisionInstanceQuery = createGetAllDecisionInstancesQuery();
    filtersSupplier.accept(decisionInstanceQuery);
    return new DecisionInstanceListRequestDto(decisionInstanceQuery);
  }


  public static ListViewRequestDto createGetAllProcessInstancesRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return new ListViewRequestDto(processInstanceQuery);
  }

  public static ListViewRequestDto createGetAllFinishedRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    return new ListViewRequestDto(createGetAllFinishedQuery(filtersSupplier));
  }

  public static ListViewRequestDto createGetAllFinishedRequest() {
    return
        new ListViewRequestDto(createProcessInstanceQuery(q -> {
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        }));
  }

  public static ListViewRequestDto createGetAllRunningRequest() {
    return
        new ListViewRequestDto(createProcessInstanceQuery(q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
        }));
  }

}