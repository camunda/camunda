/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RestAPITestUtil {

  public static ListViewQueryDto createProcessInstanceQuery(
      Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    return query;
  }

  public static ListViewQueryDto createGetProcessInstancesByIdsQuery(List<Long> ids) {
    return createGetAllProcessInstancesQuery()
        .setIds(ids.stream().map(String::valueOf).collect(Collectors.toList()));
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery() {
    return createProcessInstanceQuery(
        q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        });
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery(
      Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery(
      Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllFinishedQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery() {
    return createProcessInstanceQuery(
        q -> {
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        });
  }

  public static ListViewQueryDto createGetAllRunningQuery() {
    return createProcessInstanceQuery(
        q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
        });
  }

  public static ListViewRequestDto createProcessInstanceRequest(
      Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewRequestDto request = new ListViewRequestDto();
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    request.setQuery(query);
    return request;
  }

  public static ListViewRequestDto createGetAllProcessInstancesRequest() {
    return new ListViewRequestDto(createGetAllProcessInstancesQuery());
  }

  public static DecisionInstanceListQueryDto createDecisionInstanceQuery(
      Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
    DecisionInstanceListQueryDto query = new DecisionInstanceListQueryDto();
    filtersSupplier.accept(query);
    return query;
  }

  public static DecisionInstanceListRequestDto createDecisionInstanceRequest(
      Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
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
    return new DecisionInstanceListRequestDto(createGetAllDecisionInstancesQuery());
  }

  public static DecisionInstanceListRequestDto createGetAllDecisionInstancesRequest(
      Consumer<DecisionInstanceListQueryDto> filtersSupplier) {
    final DecisionInstanceListQueryDto decisionInstanceQuery = createGetAllDecisionInstancesQuery();
    filtersSupplier.accept(decisionInstanceQuery);
    return new DecisionInstanceListRequestDto(decisionInstanceQuery);
  }

  public static ListViewRequestDto createGetAllProcessInstancesRequest(
      Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return new ListViewRequestDto(processInstanceQuery);
  }

  public static ListViewRequestDto createGetAllFinishedRequest(
      Consumer<ListViewQueryDto> filtersSupplier) {
    return new ListViewRequestDto(createGetAllFinishedQuery(filtersSupplier));
  }

  public static ListViewRequestDto createGetAllFinishedRequest() {
    return new ListViewRequestDto(
        createProcessInstanceQuery(
            q -> {
              q.setFinished(true);
              q.setCompleted(true);
              q.setCanceled(true);
            }));
  }

  public static ListViewRequestDto createGetAllRunningRequest() {
    return new ListViewRequestDto(
        createProcessInstanceQuery(
            q -> {
              q.setRunning(true);
              q.setActive(true);
              q.setIncidents(true);
            }));
  }
}
