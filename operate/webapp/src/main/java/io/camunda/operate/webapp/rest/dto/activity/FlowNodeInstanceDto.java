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
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FlowNodeInstanceDto {

  private String id;

  private FlowNodeType type;

  private FlowNodeStateDto state;

  private String flowNodeId;

  private OffsetDateTime startDate;

  private OffsetDateTime endDate;

  private String treePath;

  /**
   * Sort values, define the position of batch operation in the list and may be used to search for
   * previous of following page.
   */
  private SortValuesWrapper[] sortValues;

  public static FlowNodeInstanceDto createFrom(
      final FlowNodeInstanceEntity flowNodeInstanceEntity, final ObjectMapper objectMapper) {
    FlowNodeInstanceDto instance =
        new FlowNodeInstanceDto()
            .setId(flowNodeInstanceEntity.getId())
            .setFlowNodeId(flowNodeInstanceEntity.getFlowNodeId())
            .setStartDate(flowNodeInstanceEntity.getStartDate())
            .setEndDate(flowNodeInstanceEntity.getEndDate());
    if (flowNodeInstanceEntity.getState() == FlowNodeState.ACTIVE
        && flowNodeInstanceEntity.isIncident()) {
      instance.setState(FlowNodeStateDto.INCIDENT);
    } else {
      instance.setState(FlowNodeStateDto.getState(flowNodeInstanceEntity.getState()));
    }
    instance
        .setType(flowNodeInstanceEntity.getType())
        .setSortValues(
            SortValuesWrapper.createFrom(flowNodeInstanceEntity.getSortValues(), objectMapper))
        .setTreePath(flowNodeInstanceEntity.getTreePath());
    return instance;
  }

  public static List<FlowNodeInstanceDto> createFrom(
      List<FlowNodeInstanceEntity> flowNodeInstanceEntities, ObjectMapper objectMapper) {
    if (flowNodeInstanceEntities == null) {
      return new ArrayList<>();
    }
    return flowNodeInstanceEntities.stream()
        .filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public FlowNodeInstanceDto setId(String id) {
    this.id = id;
    return this;
  }

  public FlowNodeStateDto getState() {
    return state;
  }

  public FlowNodeInstanceDto setState(FlowNodeStateDto state) {
    this.state = state;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public FlowNodeInstanceDto setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public FlowNodeInstanceDto setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public FlowNodeInstanceDto setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public FlowNodeType getType() {
    return type;
  }

  public FlowNodeInstanceDto setType(FlowNodeType type) {
    this.type = type;
    return this;
  }

  public String getTreePath() {
    return treePath;
  }

  public FlowNodeInstanceDto setTreePath(final String treePath) {
    this.treePath = treePath;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public FlowNodeInstanceDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, type, state, flowNodeId, startDate, endDate, treePath);
    result = 31 * result + Arrays.hashCode(sortValues);
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
    final FlowNodeInstanceDto that = (FlowNodeInstanceDto) o;
    return Objects.equals(id, that.id)
        && type == that.type
        && state == that.state
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(treePath, that.treePath)
        && Arrays.equals(sortValues, that.sortValues);
  }
}
