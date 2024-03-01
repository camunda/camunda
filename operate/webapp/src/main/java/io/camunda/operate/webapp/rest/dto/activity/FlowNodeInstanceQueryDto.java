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
package io.camunda.operate.webapp.rest.dto.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Objects;

/** The query to get the list of batch operations, created by current user. */
public class FlowNodeInstanceQueryDto {

  private String processInstanceId;

  private String treePath;

  /** Search for the flow node instances that goes before the given sort values */
  private SortValuesWrapper[] searchBefore;

  /**
   * Search for the flow node instances that goes before the given sort values plus same sort
   * values.
   */
  private SortValuesWrapper[] searchBeforeOrEqual;

  /** Search for the flow node instances that goes exactly after the given sort values. */
  private SortValuesWrapper[] searchAfter;

  /**
   * Search for the flow node instances that goes after the given sort values plus same sort values.
   */
  private SortValuesWrapper[] searchAfterOrEqual;

  /** Page size. */
  private Integer pageSize;

  public FlowNodeInstanceQueryDto() {}

  public FlowNodeInstanceQueryDto(final String processInstanceId, final String treePath) {
    this.processInstanceId = processInstanceId;
    this.treePath = treePath;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public FlowNodeInstanceQueryDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceQueryDto setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  @Schema(
      description =
          "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"9223372036854775807\", \"1583836503404\"]")
  public Object[] getSearchBefore() {
    return searchBefore;
  }

  public FlowNodeInstanceQueryDto setSearchBefore(SortValuesWrapper[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public Object[] getSearchBefore(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBefore, objectMapper);
  }

  public SortValuesWrapper[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public FlowNodeInstanceQueryDto setSearchBeforeOrEqual(
      final SortValuesWrapper[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  public Object[] getSearchBeforeOrEqual(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchBeforeOrEqual, objectMapper);
  }

  @Schema(
      description =
          "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"1583836151645\", \"1583836128180\"]")
  public SortValuesWrapper[] getSearchAfter() {
    return searchAfter;
  }

  public FlowNodeInstanceQueryDto setSearchAfter(SortValuesWrapper[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public Object[] getSearchAfter(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfter, objectMapper);
  }

  public SortValuesWrapper[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public FlowNodeInstanceQueryDto setSearchAfterOrEqual(
      final SortValuesWrapper[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public Object[] getSearchAfterOrEqual(ObjectMapper objectMapper) {
    return SortValuesWrapper.convertSortValues(searchAfterOrEqual, objectMapper);
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public FlowNodeInstanceQueryDto setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public FlowNodeInstanceQueryDto createCopy() {
    return new FlowNodeInstanceQueryDto()
        .setSearchBefore(this.searchBefore)
        .setSearchAfter(this.searchAfter)
        .setPageSize(this.pageSize)
        .setSearchAfterOrEqual(this.searchAfterOrEqual)
        .setSearchBeforeOrEqual(this.searchBeforeOrEqual)
        .setTreePath(this.treePath)
        .setProcessInstanceId(this.processInstanceId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(processInstanceId, treePath, pageSize);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
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
    final FlowNodeInstanceQueryDto queryDto = (FlowNodeInstanceQueryDto) o;
    return Objects.equals(processInstanceId, queryDto.processInstanceId)
        && Objects.equals(treePath, queryDto.treePath)
        && Arrays.equals(searchBefore, queryDto.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, queryDto.searchBeforeOrEqual)
        && Arrays.equals(searchAfter, queryDto.searchAfter)
        && Arrays.equals(searchAfterOrEqual, queryDto.searchAfterOrEqual)
        && Objects.equals(pageSize, queryDto.pageSize);
  }
}
