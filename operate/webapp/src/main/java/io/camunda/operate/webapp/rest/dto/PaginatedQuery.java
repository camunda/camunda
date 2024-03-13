/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PaginatedQuery<T extends PaginatedQuery<T>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaginatedQuery.class);

  private static final int DEFAULT_PAGE_SIZE = 50;

  /** Page size. */
  protected Integer pageSize = DEFAULT_PAGE_SIZE;

  private SortingDto sorting;

  /** Search for process instances that goes exactly after the given sort values. */
  private SortValuesWrapper[] searchAfter;

  private SortValuesWrapper[] searchAfterOrEqual;

  /** Search for process instance that goes exactly before the given sort values. */
  private SortValuesWrapper[] searchBefore;

  private SortValuesWrapper[] searchBeforeOrEqual;

  public SortingDto getSorting() {
    return sorting;
  }

  public T setSorting(SortingDto sorting) {
    if (sorting != null && !getValidSortByValues().contains(sorting.getSortBy())) {
      throw new InvalidRequestException(
          "SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
    return (T) this;
  }

  @JsonIgnore
  protected Set<String> getValidSortByValues() {
    return new HashSet<>();
  }

  @Schema(
      description =
          "Array of values (can be one): copy/paste of sortValues field from one of the objects.",
      example = "[1605160098477, 4629710542312628000]")
  public SortValuesWrapper[] getSearchAfter() {
    return searchAfter;
  }

  public T setSearchAfter(final SortValuesWrapper[] searchAfter) {
    this.searchAfter = searchAfter;
    return (T) this;
  }

  public Object[] getSearchAfter(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfter, objectMapper);
  }

  public SortValuesWrapper[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public T setSearchAfterOrEqual(final SortValuesWrapper[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return (T) this;
  }

  public Object[] getSearchAfterOrEqual(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfterOrEqual, objectMapper);
  }

  @Schema(
      description =
          "Array of values (can be one): copy/paste of sortValues field from one of the objects.",
      example = "[1605160098477, 4629710542312628000]")
  public SortValuesWrapper[] getSearchBefore() {
    return searchBefore;
  }

  public T setSearchBefore(final SortValuesWrapper[] searchBefore) {
    this.searchBefore = searchBefore;
    return (T) this;
  }

  public Object[] getSearchBefore(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBefore, objectMapper);
  }

  public SortValuesWrapper[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public T setSearchBeforeOrEqual(final SortValuesWrapper[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return (T) this;
  }

  public Object[] getSearchBeforeOrEqual(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBeforeOrEqual, objectMapper);
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public T setPageSize(final Integer pageSize) {
    this.pageSize = pageSize;
    return (T) this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(sorting, pageSize);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PaginatedQuery that = (PaginatedQuery) o;
    return Objects.equals(sorting, that.sorting)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchAfterOrEqual, that.searchAfterOrEqual)
        && Arrays.equals(searchBefore, that.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, that.searchBeforeOrEqual)
        && Objects.equals(pageSize, that.pageSize);
  }
}
