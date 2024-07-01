/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.page;

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
