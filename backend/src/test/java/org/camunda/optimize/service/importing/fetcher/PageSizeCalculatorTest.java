package org.camunda.optimize.service.importing.fetcher;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class PageSizeCalculatorTest {

  private static final int MAX_PAGE_SIZE = 10;
  private static final int MIN_PAGE_SIZE = 1;

  @Test
  public void newPageSizeRemainsConstantWhenHittingTheOptimizeDuration() {
    // given
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(15_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);
    int oldPageSize = pageSizeCalculator.getCalculatedPageSize();

    // when
    pageSizeCalculator.calculateNewPageSize(pageSizeCalculator.getOptimalEngineReadDurationInMs());

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), is(oldPageSize));
  }

  @Test
  public void pageSizeDecreasesWhenDurationTookTooLong() {
    // given
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(15_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);
    // increase page size
    pageSizeCalculator.calculateNewPageSize(0L);
    int oldPageSize = pageSizeCalculator.getCalculatedPageSize();

    // when
    pageSizeCalculator.calculateNewPageSize(Long.MAX_VALUE);

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), lessThan(oldPageSize));
  }

  @Test
  public void pageSizeIncreasesWhenDurationWasTooShort() {
    // given
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(10_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);
    int oldPageSize = pageSizeCalculator.getCalculatedPageSize();

    // when
    pageSizeCalculator.calculateNewPageSize(500L);

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), greaterThan(oldPageSize));
  }

  @Test
  public void initialPageSizeIsMinimum() {
    // when
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(10_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), is(MIN_PAGE_SIZE));
  }

  @Test
  public void pageSizeDoesNotExceedMaximum() {
    // given
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(10_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);

    // when
    pageSizeCalculator.calculateNewPageSize(0L);

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), is(MAX_PAGE_SIZE));
  }

  @Test
  public void pageSizeDoesNotUndercutMinimum() {
    // given
    PageSizeCalculator pageSizeCalculator =
      new PageSizeCalculator(10_000, MAX_PAGE_SIZE, MIN_PAGE_SIZE);

    // when
    pageSizeCalculator.calculateNewPageSize(Long.MAX_VALUE);

    // then
    assertThat(pageSizeCalculator.getCalculatedPageSize(), is(MIN_PAGE_SIZE));
  }

}
