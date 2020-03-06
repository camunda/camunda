/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.es.reader.ElasticsearchHelper.clearScroll;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class ScrollBasedImportIndexHandler
  implements ImportIndexHandler<IdSetBasedImportPage, AllEntitiesBasedImportIndexDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ImportIndexReader importIndexReader;
  @Autowired
  protected OptimizeElasticsearchClient esClient;
  @Autowired
  protected ConfigurationService configurationService;

  protected String scrollId;

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  private Long importIndex = 0L;

  private Set<String> fetchNextPageOfIds() {
    Set<String> ids;
    if (scrollId == null) {
      ids = performInitialSearchQuery();
    } else {
      try {
        ids = performScrollQuery();
      } catch (Exception e) {
        //scroll got lost, try again after reset
        this.resetScroll();
        ids = performInitialSearchQuery();
      }
    }
    if (ids.isEmpty()) {
      resetScroll();
    }
    return ids;
  }

  protected abstract Set<String> performScrollQuery();

  protected abstract Set<String> performInitialSearchQuery();

  private void resetScroll() {
    String currentScrollId = scrollId;
    scrollId = null;
    importIndex = 0L;
    if (currentScrollId != null) {
      try {
        clearScroll(this.getClass(), esClient, currentScrollId);
      } catch (ElasticsearchStatusException ex) {
        logger.warn("Could not clear scroll. The scroll might have already been expired.");
      }
    }
  }

  protected abstract String getElasticsearchTypeForStoring();

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
    updateIndex(ids.size());
    return page;
  }

  public void updateIndex(int pageSize) {
    importIndex += pageSize;
  }

  private String getElasticsearchId() {
    return EsHelper.constructKey(getElasticsearchTypeForStoring(), getEngineAlias());
  }

  @Override
  public AllEntitiesBasedImportIndexDto createIndexInformationForStoring() {
    AllEntitiesBasedImportIndexDto importIndexDto = new AllEntitiesBasedImportIndexDto();
    importIndexDto.setEsTypeIndexRefersTo(getElasticsearchTypeForStoring());
    importIndexDto.setImportIndex(importIndex);
    importIndexDto.setEngine(getEngineAlias());
    return importIndexDto;
  }

  @Override
  public void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(getElasticsearchId());
    storedIndex.ifPresent(
      allEntitiesBasedImportIndexDto -> importIndex = allEntitiesBasedImportIndexDto.getImportIndex()
    );
  }

  @Override
  public void resetImportIndex() {
    logger.debug("Resetting import index");
    resetScroll();
    importIndex = 0L;
  }

  @Override
  public void executeAfterMaxBackoffIsReached() {
    // nothing to do here
  }

}
