/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.page.ImportPage;

public interface ImportIndexHandler<PAGE extends ImportPage, INDEX_DTO> {

  /**
   * Retrieves all information of the next page to import.
   */
  default PAGE getNextPage() {
    return null;
  }

  /**
   * Creates a data transfer object (DTO) of the current index state.
   * On every restart of Optimize this information can be used to continue the import where it stopped the last time.
   */
  INDEX_DTO getIndexStateDto();

  /**
   * Resets the import index such that it can start the import
   * all over again. E.g., that can be helpful to import
   * entities that were missed during the first round.
   */
  void resetImportIndex();

}
