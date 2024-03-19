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
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.operate.util.ExceptionHelper.withIOException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, RequestDSL.QueryType.ALL)
            .query(
                withTenantCheck(
                    ids(toSafeListOfStrings(map(processInstanceIds, Object::toString)))));

    final Map<Long, String> processInstanceId2IndexName =
        withIOException(
            () ->
                richOpenSearchClient
                    .doc()
                    .search(searchRequestBuilder, Void.class)
                    .hits()
                    .hits()
                    .stream()
                    .collect(Collectors.toMap(hit -> Long.valueOf(hit.id()), Hit::index)));

    if (processInstanceId2IndexName.isEmpty()) {
      throw new NotFoundException(
          String.format("Process instances %s doesn't exists.", processInstanceIds));
    }

    return processInstanceId2IndexName;
  }

  @Override
  public String findProcessInstanceTreePathFor(long processInstanceKey) {
    record Result(String treePath) {}
    final RequestDSL.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? RequestDSL.QueryType.ALL
            : RequestDSL.QueryType.ONLY_RUNTIME;
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, queryType)
            .query(withTenantCheck(term(ListViewTemplate.KEY, processInstanceKey)))
            .source(sourceInclude(ListViewTemplate.TREE_PATH));

    final List<Hit<Result>> hits =
        richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.size() > 0) {
      return hits.get(0).source().treePath();
    }
    return null;
  }

  @Override
  public List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(Long processDefinitionKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate.getAlias())
            .query(
                withTenantCheck(
                    constantScore(
                        and(
                            term(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
                            not(exists(ListViewTemplate.PROCESS_VERSION))))))
            .source(s -> s.fetch(false));

    return richOpenSearchClient
        .doc()
        .search(searchRequestBuilder, Void.class)
        .hits()
        .hits()
        .stream()
        .map(hit -> Long.valueOf(hit.id()))
        .toList();
  }
}
