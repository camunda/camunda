package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

@Component
public abstract class MissingEntitiesFinder<ENG extends EngineDto> {

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  private Client esclient;

  public List<ENG> retrieveMissingEntities(List<ENG> engineEntities) {

    Set<String> idsAlreadyAddedToOptimize = getIdsOfDocumentsAlreadyInElasticsearch(engineEntities);
    List<ENG> newEngineEntities = removeAlreadyAddedEntities(idsAlreadyAddedToOptimize, engineEntities);

    return newEngineEntities;
  }

  private List<ENG> removeAlreadyAddedEntities(Set<String> idsAlreadyAddedToOptimize, List<ENG> engineEntities) {
    List<ENG> newEngineEntities = new ArrayList<>(engineEntities.size());
    for (ENG engineEntity : engineEntities) {
      if (!idsAlreadyAddedToOptimize.contains(engineEntity.getId())) {
        newEngineEntities.add(engineEntity);
      }
    }
    return newEngineEntities;
  }

  private Set<String> getIdsOfDocumentsAlreadyInElasticsearch(List<ENG> engineEntities) {

    String[] engineIds = getEngineIds(engineEntities);
    QueryBuilder qb = idsQuery(elasticSearchType())
        .addIds(engineIds);

    SearchResponse idsResp = esclient.prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(elasticSearchType())
        .setQuery(qb)
        .setFetchSource(false)
        .setSize(configurationService.getEngineImportMaxPageSize())
        .get();

    Set<String> idsAlreadyAddedToOptimize = new HashSet<>();
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      idsAlreadyAddedToOptimize.add(searchHit.getId());
    }

    return idsAlreadyAddedToOptimize;
  }

  private String[] getEngineIds(List<ENG> engineEntities) {
    String[] engineIds = new String[engineEntities.size()];
    for (int i = 0; i < engineEntities.size(); i++) {
      engineIds[i] = engineEntities.get(i).getId();
    }
    return engineIds;
  }

  /**
   * @return The elasticsearch type where we can find the
   * corresponding entries from the engine in elasticsearch
   */
  protected abstract String elasticSearchType();

}
