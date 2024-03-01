/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.rest.dto.listview;

import io.camunda.operate.webapp.rest.dto.PaginatedQuery;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Schema(description = "Process instances request")
public class ListViewRequestDto extends PaginatedQuery<ListViewRequestDto> {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_PROCESS_NAME = "processName";
  public static final String SORT_BY_WORFLOW_VERSION = "processVersion";
  public static final String SORT_BY_PARENT_INSTANCE_ID = "parentInstanceId";
  public static final String SORT_BY_TENANT_ID = "tenant";

  public static final Set<String> VALID_SORT_BY_VALUES;

  static {
    VALID_SORT_BY_VALUES = new HashSet<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_PROCESS_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
    VALID_SORT_BY_VALUES.add(SORT_BY_PARENT_INSTANCE_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_TENANT_ID);
  }

  private ListViewQueryDto query;

  public ListViewRequestDto() {}

  public ListViewRequestDto(final ListViewQueryDto query) {
    this.query = query;
  }

  public ListViewQueryDto getQuery() {
    return query;
  }

  public void setQuery(final ListViewQueryDto query) {
    this.query = query;
  }

  @Override
  protected Set<String> getValidSortByValues() {
    return VALID_SORT_BY_VALUES;
  }

  @Override
  public ListViewRequestDto setSearchAfterOrEqual(final SortValuesWrapper[] searchAfterOrEqual) {
    if (searchAfterOrEqual != null) {
      throw new InvalidRequestException("SearchAfterOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public ListViewRequestDto setSearchBeforeOrEqual(final SortValuesWrapper[] searchBeforeOrEqual) {
    if (searchBeforeOrEqual != null) {
      throw new InvalidRequestException("SearchBeforeOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ListViewRequestDto that = (ListViewRequestDto) o;
    return Objects.equals(query, that.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), query);
  }
}
