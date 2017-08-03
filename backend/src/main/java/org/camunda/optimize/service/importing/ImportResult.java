package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.index.ImportIndexHandler;

import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class ImportResult <IH extends ImportIndexHandler> {
  private boolean engineHasStillNewData;
  private Set<String> idsToFetch;

  //fields of results for paginated executions only
  private int searchedSize;
  private Class<IH> indexHandlerType;
  private String elasticSearchType;

  public boolean getEngineHasStillNewData() {
    return engineHasStillNewData;
  }

  public void setEngineHasStillNewData(boolean engineHasStillNewData) {
    this.engineHasStillNewData = engineHasStillNewData;
  }

  public Set<String> getIdsToFetch() {
    return idsToFetch;
  }

  public void setIdsToFetch(Set<String> idsToFetch) {
    this.idsToFetch = idsToFetch;
  }

  public void setSearchedSize(int searchedSize) {
    this.searchedSize = searchedSize;
  }

  public int getSearchedSize() {
    return searchedSize;
  }

  public void setIndexHandlerType(Class<IH> indexHandlerType) {
    this.indexHandlerType = indexHandlerType;
  }

  public Class<IH> getIndexHandlerType() {
    return indexHandlerType;
  }

  public String getElasticSearchType() {
    return elasticSearchType;
  }

  public void setElasticSearchType(String elasticSearchType) {
    this.elasticSearchType = elasticSearchType;
  }
}
