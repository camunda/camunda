/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.page;

public class AllEntitiesBasedImportPage implements ImportPage {

  protected long indexOfFirstResult;
  protected long pageSize;

  public long getIndexOfFirstResult() {
    return indexOfFirstResult;
  }

  public void setIndexOfFirstResult(long indexOfFirstResult) {
    this.indexOfFirstResult = indexOfFirstResult;
  }

  public long getPageSize() {
    return pageSize;
  }

  public void setPageSize(long pageSize) {
    this.pageSize = pageSize;
  }
}
