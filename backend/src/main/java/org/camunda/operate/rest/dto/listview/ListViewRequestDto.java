/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.rest.dto.listview;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.exception.InvalidRequestException;

public class ListViewRequestDto {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_WORKFLOW_NAME = "workflowName";
  public static final String SORT_BY_WORFLOW_VERSION = "workflowVersion";

  public static final List<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new ArrayList<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORKFLOW_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
  }

  public ListViewRequestDto() {
  }

  public ListViewRequestDto(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  private List<ListViewQueryDto> queries = new ArrayList<>();

  private SortingDto sorting;

  public List<ListViewQueryDto> getQueries() {
    return queries;
  }

  public void setQueries(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  public SortingDto getSorting() {
    return sorting;
  }

  public void setSorting(SortingDto sorting) {
    if (sorting != null && !VALID_SORT_BY_VALUES.contains(sorting.getSortBy())) {
      throw new InvalidRequestException("SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewRequestDto that = (ListViewRequestDto) o;

    if (queries != null ? !queries.equals(that.queries) : that.queries != null)
      return false;
    return sorting != null ? sorting.equals(that.sorting) : that.sorting == null;
  }

  @Override
  public int hashCode() {
    int result = queries != null ? queries.hashCode() : 0;
    result = 31 * result + (sorting != null ? sorting.hashCode() : 0);
    return result;
  }

}
