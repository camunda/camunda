package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  private Set<String> alreadyImportedIds = new HashSet<>();


  public ProcessDefinitionImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchImportIndexType() {
    return ElasticsearchConstants.PROC_DEF_TYPE;
  }

  public void addImportedDefinitions(Collection<ProcessDefinitionEngineDto> definitions) {
    definitions.forEach(d -> alreadyImportedIds.add(d.getId()));
    moveImportIndex(definitions.size());
  }

  public List<ProcessDefinitionEngineDto> filterNewDefinitions(List<ProcessDefinitionEngineDto> engineEntities) {
    return engineEntities
      .stream()
      .filter(def -> !alreadyImportedIds.contains(def.getId()))
      .collect(Collectors.toList());
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    alreadyImportedIds.clear();
  }
}
