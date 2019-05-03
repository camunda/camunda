/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.writer;

import java.io.IOException;
import java.util.Collection;

import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.CollectionUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncidentWriter {
  
  private final static Logger logger = LoggerFactory.getLogger(IncidentWriter.class);
  
  @Autowired
  IncidentTemplate incidentTemplate;
  
  @Autowired
  RestHighLevelClient esClient;
  
  public long updateWorkflowIds(String workflowId, Collection<String> workflowInstanceIds) throws PersistenceException {
    String workflowIdField = ListViewTemplate.WORKFLOW_ID;
    TermsQueryBuilder query = QueryBuilders.termsQuery(IncidentTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceIds);
    
    Script script = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG,
                               "ctx._source." + workflowIdField + " = params." + workflowIdField,
                               CollectionUtil.asMap(workflowIdField, workflowId));
    
    UpdateByQueryRequest request = new UpdateByQueryRequest(incidentTemplate.getAlias())
                                    .setQuery(query).setScript(script)
                                    .setRefresh(true);
    
    try {
      return esClient.updateByQuery(request, RequestOptions.DEFAULT).getUpdated();
    } catch (IOException e) {
      logger.error("Error preparing the query to update incidents", e);
      throw new PersistenceException(String.format("Error preparing the query to update incidents for workflow id [%s]", workflowId), e);
    }
  }

}
