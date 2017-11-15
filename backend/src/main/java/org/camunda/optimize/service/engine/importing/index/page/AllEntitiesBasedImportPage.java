package org.camunda.optimize.service.engine.importing.index.page;

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
