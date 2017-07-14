package org.camunda.optimize.service.importing;

import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class ImportResult {
  private boolean engineHasStillNewData;
  private Set<String> idsToFetch;

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
}
