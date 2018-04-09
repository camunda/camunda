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
import java.util.OptionalDouble;
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

  private Long maxEntityCount = 0L;
  private Long importIndex = 0L;

  private Set<String> fetchNextPageOfProcessInstanceIds() {
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

  protected abstract long fetchMaxEntityCount();

  protected abstract String getElasticsearchTrackingType();

  @Override
  public Optional<IdSetBasedImportPage> getNextPage() {
    Set<String> ids = fetchNextPageOfProcessInstanceIds();
    if (ids.isEmpty()) {
      resetScroll();
      //it might be the case that new PI's have been imported
      ids = fetchNextPageOfProcessInstanceIds();
    }
    IdSetBasedImportPage page = new IdSetBasedImportPage();
    page.setIds(ids);
    importIndex += ids.size();
    storeIdsForTracking(ids);
    return Optional.of(page);
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
      "ctx._source.ids.addAll(params.ids)",
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

  public OptionalDouble computeProgress() {
    updateMaxEntityCount();
    Long maxEntityCount = this.maxEntityCount;
    Long importIndex = this.importIndex;

    if (hasNothingToImport(importIndex, maxEntityCount)) {
      return OptionalDouble.empty();
    } else if (indexReachedMaxCount()) {
      return OptionalDouble.of(100.0);
    } else {
      Long maxCount = Math.max(1, maxEntityCount);
      double calculatedProgress = importIndex.doubleValue() / maxCount.doubleValue() * 100.0;
      calculatedProgress = Math.min(100.0, calculatedProgress);
      return OptionalDouble.of(calculatedProgress);
    }
  }

  private boolean hasNothingToImport(long importIndex, long maxEntityCount) {
    return importIndex == 0 && maxEntityCount == 0;
  }

  public boolean hasNewPage() {
    if( indexReachedMaxCount()) {
      resetScroll();
    }
    updateMaxEntityCount();
    return this.importIndex < this.maxEntityCount;
  }

  private void updateMaxEntityCount() {
    this.maxEntityCount = fetchMaxEntityCount();
    importIndex = maxEntityCount < importIndex? maxEntityCount : importIndex;
  }

  private boolean indexReachedMaxCount() {
    return importIndex >= maxEntityCount;
  }


  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto importIndexDto = new AllEntitiesBasedImportIndexDto();
    importIndexDto.setEsTypeIndexRefersTo(getElasticsearchTrackingType());
    importIndexDto.setMaxEntityCount(maxEntityCount);
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
      maxEntityCount = storedIndex.get().getMaxEntityCount();
    }
  }

  @Override
  public void resetImportIndex() {
    logger.debug("Resetting import index");
    resetScroll();
    resetElasticsearchTrackingType();
    importIndex = 0L;
    this.maxEntityCount = 0L;
    updateMaxEntityCount();
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
  public void restartImportCycle() {
    // nothing to do here
  }

  @Override
  public EngineContext getEngineContext() {
    return engineContext;
  }
}
