package org.camunda.optimize.service.engine.importing.index.page;

public class DefinitionBasedImportPage implements ImportPage {

  protected long indexOfFirstResult;
  protected long pageSize;
  protected String currentProcessDefinitionId;

  public long getIndexOfFirstResult() {
    return indexOfFirstResult;
  }

  public void setIndexOfFirstResult(long indexOfFirstResult) {
    this.indexOfFirstResult = indexOfFirstResult;
  }

  public String getCurrentProcessDefinitionId() {
    return currentProcessDefinitionId;
  }

  public void setCurrentProcessDefinitionId(String currentProcessDefinitionId) {
    this.currentProcessDefinitionId = currentProcessDefinitionId;
  }

  public long getPageSize() {
    return pageSize;
  }

  public void setPageSize(long pageSize) {
    this.pageSize = pageSize;
  }
}
