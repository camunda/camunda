/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.FNI;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class UpdateIncidentsFromProcessInstancesAction implements Callable<Void> {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  private List<String> processInstanceKeys;

  public UpdateIncidentsFromProcessInstancesAction(final List<String> processInstanceKeys) {
    this.processInstanceKeys = processInstanceKeys;
  }

  @Override
  public Void call() throws Exception {
    if (processInstanceKeys.size() > 0) {
      //force refresh of incident
      final RefreshRequest refreshRequest = new RefreshRequest(
          listViewTemplate.getFullQualifiedName());
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      //select tree paths
      final QueryBuilder query = joinWithAnd(termQuery(ListViewTemplate.JOIN_RELATION,
          ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
          termsQuery(ListViewTemplate.KEY, processInstanceKeys));
      SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate)
          .source(new SearchSourceBuilder().query(query)
              .fetchSource(new String[]{ListViewTemplate.TREE_PATH, ListViewTemplate.KEY}, null));
      Map<String, String> treePathMap = new HashMap<>();
      scrollWith(searchRequest, esClient, sh ->
              Arrays.stream(sh.getHits()).map(SearchHit::getSourceAsMap)
                  .forEach(fieldMap -> treePathMap.put(String.valueOf(fieldMap.get(ListViewTemplate.KEY)),
                      (String) fieldMap.get(ListViewTemplate.TREE_PATH)))
          , null, null);

      //update incidents with tree paths
      final String script = "String processInstanceKey = String.valueOf(ctx._source.processInstanceKey);"
          + "if (params.treePathMap.get(processInstanceKey) != null) {"
          + "   ctx._source.treePath = params.treePathMap.get(processInstanceKey) + '/" + FNI
          + "_' + ctx._source.flowNodeInstanceKey;"
          + "}";
      UpdateByQueryRequest request = new UpdateByQueryRequest(incidentTemplate.getFullQualifiedName())
          .setQuery(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, treePathMap.keySet()))
          .setScript(new Script(
              ScriptType.INLINE,
              Script.DEFAULT_SCRIPT_LANG,
              script,
              asMap("treePathMap", treePathMap)
          ));
      esClient.updateByQuery(request, RequestOptions.DEFAULT);
    }
    return null;
  }
}
