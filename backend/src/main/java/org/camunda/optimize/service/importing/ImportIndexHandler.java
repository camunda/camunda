/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.service.importing.page.ImportPage;

public interface ImportIndexHandler<PAGE extends ImportPage, INDEX extends ImportIndexDto> {

  /**
   * Retrieves all information to import a new page from the engine. With
   * especially an offset where to start the import and the number of
   * instances to fetch.
   */
  default PAGE getNextPage() {
    return null;
  }

  /**
   * Creates a data transfer object (DTO) about an index to store that
   * to Elasticsearch. On every restart of Optimize this information
   * can be used to continue the import where it stopped the last time.
   */
  INDEX createIndexInformationForStoring();

  /**
   * Initializes the import index.
   */
  void readIndexFromElasticsearch();


  /**
   * Resets the import index such that it can start the import
   * all over again. E.g., that can be helpful to import
   * entities that were missed during the first round.
   */
  void resetImportIndex();

  void executeAfterMaxBackoffIsReached();

  String getEngineAlias();

}
