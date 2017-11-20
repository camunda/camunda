package org.camunda.optimize.service.engine.importing.index.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.util.EsHelper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.UnfinishedProcessInstanceTrackingType.PROCESS_INSTANCE_IDS;

@Component
public abstract class ScrollBasedImportIndexHandler
  extends BackoffImportIndexHandler<IdSetBasedImportPage, AllEntitiesBasedImportIndexDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  protected ImportIndexReader importIndexReader;

  @Autowired
  protected Client esclient;

  protected String engineAlias;

  @Override
  protected void init() {
    // nothing initialize here
  }

  private Long maxEntityCount = 0L;
  private Long importIndex = 0L;

  protected abstract Set<String> fetchNextPageOfProcessInstanceIds();

  protected abstract void resetScroll();

  protected abstract long fetchMaxEntityCount();

  protected abstract String getElasticsearchTrackingType();

  @Override
  public Optional<IdSetBasedImportPage> getNextImportPage() {
    Set<String> ids = fetchNextPageOfProcessInstanceIds();
    if (ids.isEmpty()) {
      resetScroll();
      ids = fetchNextPageOfProcessInstanceIds();
      if (ids.isEmpty()) {
        updateMaxEntityCount();
        return Optional.empty();
      }
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
    try {
      bulkRequest.add(buildIdStoringRequest(idsAsList));
    } catch (IOException e) {
      e.printStackTrace();
    }
    bulkRequest.get();
  }

  private UpdateRequestBuilder buildIdStoringRequest(List<String> ids) throws IOException {

    Map<String, Object> params = new HashMap<>();
    params.put("processInstanceIds", ids);
    Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.processInstanceIds.addAll(params.processInstanceIds)",
      params
    );

    Map<String, List<String>> newEntryIfAbsent = new HashMap<>();
    newEntryIfAbsent.put(PROCESS_INSTANCE_IDS, ids);

    return esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(),
        getElasticsearchTrackingType(),
        getElasticsearchId())
      .setScript(updateScript)
      .setUpsert(newEntryIfAbsent)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict());
  }

  private String getElasticsearchId() {
    return getElasticsearchTrackingType() + "-" + engineAlias;
  }

  public OptionalDouble computeProgress() {
    if (hasNothingToImport()) {
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

  private boolean hasNothingToImport() {
    return importIndex == 0L && maxEntityCount == 0L;
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
    return importIndexDto;
  }

  @Override
  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(EsHelper.constructKey(getElasticsearchTrackingType(), engineAlias));
    if (storedIndex.isPresent()) {
      importIndex = storedIndex.get().getImportIndex();
      maxEntityCount = storedIndex.get().getMaxEntityCount();
    }
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    resetScroll();
    importIndex = 0L;
    updateMaxEntityCount();
  }

  @Override
  public String getEngineAlias() {
    return engineAlias;
  }
}
