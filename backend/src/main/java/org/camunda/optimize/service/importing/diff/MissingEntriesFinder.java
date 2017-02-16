package org.camunda.optimize.service.importing.diff;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

@Component
public abstract class MissingEntriesFinder<ENG extends EngineDto> {

  @Autowired
  protected ConfigurationService configurationService;

  @Autowired
  protected Client client;

  @Autowired
  private TransportClient esclient;

  private Map<String, ENG> engineEntries = new HashMap<>();

  public List<ENG> retrieveMissingEntities(int indexOfFirstResult, int maxPageSize) {
    Set<String> engineIds = getIdsFromEngine(indexOfFirstResult, maxPageSize);
    Set<String> missingDocumentIds = getMissingDocumentsFromElasticSearch(engineIds);
    List<ENG> newEngineEntities = retrieveMissingEntities(missingDocumentIds);

    engineEntries.clear();
    return newEngineEntities;
  }

  private List<ENG> retrieveMissingEntities(Set<String> ids) {
    return ids.stream().map(id -> engineEntries.get(id)).collect(Collectors.toList());
  }

  private Set<String> getIdsFromEngine(int indexOfFirstResult, int maxPageSize) {
    Set<String> ids = new HashSet<>();
    List<ENG> entries = queryEngineRestPoint(indexOfFirstResult, maxPageSize);

    for (ENG entry : entries) {
      ids.add(entry.getId());
      engineEntries.put(entry.getId(), entry);
    }
    return ids;
  }

  private Set<String> getMissingDocumentsFromElasticSearch(Set<String> engineIds) {
    QueryBuilder qb = idsQuery(elasticSearchType())
      .addIds(engineIds.toArray(new String[engineIds.size()]));
    SearchResponse idsResp = esclient.prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(elasticSearchType())
      .setQuery(qb)
      .setSize(configurationService.getEngineImportMaxPageSize())
      .get();

    Set<String> idsAlreadyAddedToOptimize = new HashSet<>();
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      idsAlreadyAddedToOptimize.add(searchHit.getId());
    }

    Set<String> missingDocumentIds;
    missingDocumentIds = removeAllEntriesThatAreAlreadyInOptimize(engineIds, idsAlreadyAddedToOptimize);
    return missingDocumentIds;
  }

  private Set<String> removeAllEntriesThatAreAlreadyInOptimize(Set<String> engineIds, Set<String> optimizeIds) {
    engineIds.removeAll(optimizeIds);
    return engineIds;
  }

  /**
   * @return All entries from the engine that we want to
   * import to optimize, i.e. elasticsearch
   */
  protected abstract List<ENG> queryEngineRestPoint(int indexOfFirstResult, int maxPageSize);

  /**
   * @return The elasticsearch type where we can find the
   * corresponding entries from the engine in elasticsearch
   */
  protected abstract String elasticSearchType();

}
