package org.camunda.optimize.service.importing;

import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class ImportResult {
  private int pagesPassed;
  private Set<String> idsToFetch;

  public int getPagesPassed() {
    return pagesPassed;
  }

  public void setPagesPassed(int pagesPassed) {
    this.pagesPassed = pagesPassed;
  }

  public Set<String> getIdsToFetch() {
    return idsToFetch;
  }

  public void setIdsToFetch(Set<String> idsToFetch) {
    this.idsToFetch = idsToFetch;
  }
}
