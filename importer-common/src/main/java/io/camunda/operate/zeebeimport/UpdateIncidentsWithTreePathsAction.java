/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.zeebeimport.util.TreePath.TreePathEntryType.FNI;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.schema.templates.IncidentTemplate;
import java.util.Map;
import java.util.concurrent.Callable;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class UpdateIncidentsWithTreePathsAction implements Callable<Void> {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private IncidentTemplate incidentTemplate;

  private Map<String, String> treePathMap;

  public UpdateIncidentsWithTreePathsAction(final Map<String, String> treePathMap) {
    this.treePathMap = treePathMap;
  }

  @Override
  public Void call() throws Exception {
    if (treePathMap.size() > 0) {
      //force refresh of incident
      final RefreshRequest refreshRequest = new RefreshRequest(
          incidentTemplate.getFullQualifiedName());
      esClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
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
