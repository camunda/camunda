/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.search;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.impl.search.response.ProcessInstanceImpl;
import io.camunda.client.impl.search.response.SearchQueryResponseImpl;
import io.camunda.client.impl.search.response.SearchResponsePageImpl;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.UserTaskItem;
import io.camunda.client.protocol.rest.UserTaskSearchQueryResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SearchResponseMapper {

  private SearchResponseMapper() {}

  public static SearchQueryResponse<ProcessInstance> toProcessInstanceSearchResponse(
      final ProcessInstanceSearchQueryResponse response) {
    final SearchQueryPageResponse pageResponse = response.getPage();
    final SearchResponsePage page =
        new SearchResponsePageImpl(
            pageResponse.getTotalItems(),
            pageResponse.getFirstSortValues(),
            pageResponse.getLastSortValues());

    final List<ProcessInstance> instances =
        Optional.ofNullable(response.getItems())
            .map(
                (i) ->
                    i.stream()
                        .map(ProcessInstanceImpl::new)
                        .map((p) -> (ProcessInstance) p)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());

    return new SearchQueryResponseImpl<>(instances, page);
  }

  public static SearchQueryResponse<UserTaskItem> toUserTaskSearchResponse(
      final UserTaskSearchQueryResponse response) {
    final SearchQueryPageResponse pageResponse = response.getPage();
    final SearchResponsePage page = toSearchResponsePage(pageResponse);

    return new SearchQueryResponseImpl<>(response.getItems(), page);
  }

  private static SearchResponsePage toSearchResponsePage(
      final SearchQueryPageResponse pageResponse) {
    return new SearchResponsePageImpl(
        pageResponse.getTotalItems(),
        pageResponse.getFirstSortValues(),
        pageResponse.getLastSortValues());
  }
}
