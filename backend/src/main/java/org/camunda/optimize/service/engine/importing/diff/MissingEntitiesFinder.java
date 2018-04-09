package org.camunda.optimize.service.engine.importing.diff;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

public class MissingEntitiesFinder<ENG extends EngineDto> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private Client esclient;

  private ConfigurationService configurationService;
  private String elasticseachType;

  public MissingEntitiesFinder(ConfigurationService configurationService, Client esclient, String elasticseachType) {
    this.configurationService = configurationService;
    this.esclient = esclient;
    this.elasticseachType = elasticseachType;
  }

  public List<ENG> retrieveMissingEntities(List<ENG> engineEntities) {

    Set<String> idsAlreadyAddedToOptimize;
    try {
      idsAlreadyAddedToOptimize = getIdsOfDocumentsAlreadyInElasticsearch(engineEntities);
    } catch (Exception e) {
      logger.debug("Could not fetch already imported entities from Elasticsearch", e);
      idsAlreadyAddedToOptimize = new HashSet<>();
    }
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
    QueryBuilder qb = idsQuery(elasticseachType)
        .addIds(engineIds);

    Set<String> idsAlreadyAddedToOptimize = new HashSet<>();
    SearchResponse scrollResp = esclient
        .prepareSearch(configurationService.getOptimizeIndex(elasticseachType))
        .setTypes(elasticseachType)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(qb)
        .setSize(1000)
      .get();

    do {
      for (SearchHit searchHit : scrollResp.getHits().getHits()) {
        idsAlreadyAddedToOptimize.add(searchHit.getId());
      }

      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);

    return idsAlreadyAddedToOptimize;
  }

  private String[] getEngineIds(List<ENG> engineEntities) {
    String[] engineIds = new String[engineEntities.size()];
    for (int i = 0; i < engineEntities.size(); i++) {
      engineIds[i] = engineEntities.get(i).getId();
    }
    return engineIds;
  }

}
