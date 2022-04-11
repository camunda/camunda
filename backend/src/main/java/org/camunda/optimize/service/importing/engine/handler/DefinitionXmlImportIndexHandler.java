/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ImportIndexReader;
import org.camunda.optimize.service.importing.EngineImportIndexHandler;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class DefinitionXmlImportIndexHandler
  implements EngineImportIndexHandler<IdSetBasedImportPage, AllEntitiesBasedImportIndexDto> {

  @Autowired
  protected OptimizeElasticsearchClient esClient;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  private ImportIndexReader importIndexReader;
  private Long importIndex = 0L;

  @Override
  public IdSetBasedImportPage getNextPage() {
    Set<String> ids = performSearchQuery();
    IdSetBasedImportPage page = new IdSetBasedImportPage();
    page.setIds(ids);
    updateIndex(ids.size());
    return page;
  }

  @Override
  public AllEntitiesBasedImportIndexDto getIndexStateDto() {
    AllEntitiesBasedImportIndexDto importIndexDto = new AllEntitiesBasedImportIndexDto();
    importIndexDto.setEsTypeIndexRefersTo(getElasticsearchTypeForStoring());
    importIndexDto.setImportIndex(importIndex);
    importIndexDto.setEngine(getEngineAlias());
    return importIndexDto;
  }

  @Override
  public void resetImportIndex() {
    log.debug("Resetting import index");
    importIndex = 0L;
  }

  public void updateIndex(int pageSize) {
    importIndex += pageSize;
  }

  @PostConstruct
  protected void init() {
    readIndexFromElasticsearch();
  }

  protected abstract Set<String> performSearchQuery();

  protected abstract String getElasticsearchTypeForStoring();

  private String getElasticsearchId() {
    return EsHelper.constructKey(getElasticsearchTypeForStoring(), getEngineAlias());
  }

  private void readIndexFromElasticsearch() {
    Optional<AllEntitiesBasedImportIndexDto> storedIndex =
      importIndexReader.getImportIndex(getElasticsearchId());
    storedIndex.ifPresent(
      allEntitiesBasedImportIndexDto -> importIndex = allEntitiesBasedImportIndexDto.getImportIndex()
    );
  }

}
