package org.camunda.optimize.service.importing.fetcher;

public class PageSizeCalculator {

  private int maxPageSize;
  private int minPageSize;
  private Long optimalEngineReadDurationInMs;
  private Integer currentPageSize;

  public PageSizeCalculator(int engineReadTimeout, int maxPageSize, int minPageSize) {
    determineOptimizeEngineReadDuration(engineReadTimeout);
    this.maxPageSize = maxPageSize;
    this.minPageSize = minPageSize;
    currentPageSize = minPageSize;
  }

  public void determineOptimizeEngineReadDuration(Integer engineReadTimeout) {
    // the optimal time is a fifth of the read timeout, so that when
    // the read timeout is decreased we automatically adapt the page size accordingly.
    optimalEngineReadDurationInMs = engineReadTimeout.longValue() / 5L;
  }

  public void calculateNewPageSize(Long durationOfLastEngineReadInMs) {
    float durationRatio =  durationOfLastEngineReadInMs.floatValue() / optimalEngineReadDurationInMs.floatValue();
    Integer olgPageSize = currentPageSize;
    // calculate the new page size by deviation of the duration from the optimal value
    // and adjust the page size according to that deviation
    int newPageSize = Math.round(olgPageSize.floatValue()/durationRatio);
    newPageSize = ensureDoesNotExceedMaxPageSize(newPageSize);
    newPageSize = ensureDoesNotUndercutMinPageSize(newPageSize);
    currentPageSize = newPageSize;
  }

  public int getCalculatedPageSize() {
    return currentPageSize;
  }

  public Long getOptimalEngineReadDurationInMs() {
    return optimalEngineReadDurationInMs;
  }

  private int ensureDoesNotExceedMaxPageSize(int newPageSize) {
    return Math.min(maxPageSize, newPageSize);
  }

  private int ensureDoesNotUndercutMinPageSize(int newPageSize) {
    return Math.max(minPageSize, newPageSize);
  }
}
