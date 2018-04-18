package org.camunda.optimize.service.engine.importing.index.handler;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public abstract class ScrollBasedImportIndexHandler
  implements ImportIndexHandler<IdSetBasedImportPage, AllEntitiesBasedImportIndexDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ImportIndexReader importIndexReader;
  @Autowired
  protected Client esclient;
  @Autowired
  protected ConfigurationService configurationService;

  protected EngineContext engineContext;
  protected String scrollId;

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  private Long importIndex = 0L;

  private Set<String> fetchNextPageOfIds() {
    if (scrollId == null) {
      return performInitialSearchQuery();
    } else {
      Set<String> ids;
      try {
        ids = performScrollQuery();
      } catch (Exception e) {
        //scroll got lost, try again after reset
        this.resetScroll();
        ids = performInitialSearchQuery();
      }

      return ids;
    }
  }

  protected abstract Set<String> performScrollQuery();

  protected abstract Set<String> performInitialSearchQuery();

  private void resetScroll() {
    scrollId = null;
    importIndex = 0L;
  }

  protected abstract String getElasticsearchTrackingType();

  @Override
  public IdSetBasedImportPage getNextPage() {
    Set<String> ids = fetchNextPageOfIds();
    if (ids.isEmpty()) {
      resetScroll();
      //it might be the case that new PI's have been imported
      ids = fetchNextPageOfIds();
    }
    IdSetBasedImportPage page = new IdSetBasedImportPage();
    page.setIds(ids);
    importIndex += ids.size();
    storeIdsForTracking(ids);
    return page;
  }

  private void storeIdsForTracking(Set<String> ids) {
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    List<String> idsAsList = Arrays.asList(ids.toArray(new String[]{}));
    bulkRequest.add(buildIdStoringRequest(idsAsList));
    bulkRequest.get();
  }

  private UpdateRequestBuilder buildIdStoringRequest(List<String> ids) {

    Map<String, Object> params = new HashMap<>();
    params.put("ids", ids);
    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      // the terms lookup does not work with values about 65536. Thus, we need to ensure that the value is always below
      // check OPT-1212 for more information
      "ctx._source.ids.addAll(params.ids); " +
      "if(ctx._source.ids.length > 20000) {" +
        "ctx._source.ids.removeRange(0, 10000)" +
      "}",
      params
    );

    Map<String, List<String>> newEntryIfAbsent = new HashMap<>();
    newEntryIfAbsent.put("ids", ids);

    return esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(getElasticsearchTrackingType()),
        getElasticsearchTrackingType(),
        getElasticsearchId())
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict());
  }

  private String getElasticsearchId() {
    return EsHelper.constructKey(getElasticsearchTrackingType(), engineContext.getEngineAlias());
  }

  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto importIndexDto = new AllEntitiesBasedImportIndexDto();
    importIndexDto.setEsTypeIndexRefersTo(getElasticsearchTrackingType());
    importIndexDto.setImportIndex(importIndex);
    importIndexDto.setEngine(engineContext.getEngineAlias());
    return importIndexDto;
  }

  @Override
  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(getElasticsearchId());
    if (storedIndex.isPresent()) {
      importIndex = storedIndex.get().getImportIndex();
    }
  }

  @Override
  public void resetImportIndex() {
    logger.debug("Resetting import index");
    resetScroll();
    resetElasticsearchTrackingType();
    importIndex = 0L;
  }

  private void resetElasticsearchTrackingType() {
    esclient.
      prepareDelete(configurationService.getOptimizeIndex(getElasticsearchTrackingType()),
        getElasticsearchTrackingType(),
        getElasticsearchId()
        )
      .get();
  }

  @Override
  public void executeAfterMaxBackoffIsReached() {
    // nothing to do here
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }
}
