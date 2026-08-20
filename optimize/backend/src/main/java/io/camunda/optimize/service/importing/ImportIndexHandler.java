/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.importing.page.ImportPage;

public interface ImportIndexHandler<PAGE extends ImportPage, INDEX_DTO> {

  /** Retrieves all information of the next page to import. */
  default PAGE getNextPage() {
    return null;
  }

  /**
   * Creates a data transfer object (DTO) of the current index state. On every restart of Optimize
   * this information can be used to continue the import where it stopped the last time.
   */
  INDEX_DTO getIndexStateDto();

  /**
   * Resets the import index such that it can start the import all over again. E.g., that can be
   * helpful to import entities that were missed during the first round.
   */
  void resetImportIndex();
}
