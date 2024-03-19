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
package io.camunda.operate.webapp.rest.dto.dmn.list;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DecisionInstanceForListDto {

  private String id;
  private DecisionInstanceStateDto state;
  private String decisionName;
  private Integer decisionVersion;
  private OffsetDateTime evaluationDate;
  private String processInstanceId;
  private String tenantId;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  public static DecisionInstanceForListDto createFrom(
      final DecisionInstanceEntity entity, ObjectMapper objectMapper) {
    return new DecisionInstanceForListDto()
        .setDecisionName(entity.getDecisionName())
        .setDecisionVersion(entity.getDecisionVersion())
        .setEvaluationDate(entity.getEvaluationDate())
        .setId(entity.getId())
        .setProcessInstanceId(String.valueOf(entity.getProcessInstanceKey()))
        .setState(DecisionInstanceStateDto.getState(entity.getState()))
        .setSortValues(SortValuesWrapper.createFrom(entity.getSortValues(), objectMapper))
        .setTenantId(entity.getTenantId());
  }

  public static List<DecisionInstanceForListDto> createFrom(
      List<DecisionInstanceEntity> decisionInstanceEntities, ObjectMapper objectMapper) {
    if (decisionInstanceEntities == null) {
      return new ArrayList<>();
    }
    return decisionInstanceEntities.stream()
        .filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public DecisionInstanceForListDto setId(final String id) {
    this.id = id;
    return this;
  }

  public DecisionInstanceStateDto getState() {
    return state;
  }

  public DecisionInstanceForListDto setState(final DecisionInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceForListDto setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public Integer getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceForListDto setDecisionVersion(final Integer decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceForListDto setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceForListDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceForListDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public DecisionInstanceForListDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id, state, decisionName, decisionVersion, evaluationDate, processInstanceId, tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceForListDto that = (DecisionInstanceForListDto) o;
    return Objects.equals(id, that.id)
        && state == that.state
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(decisionVersion, that.decisionVersion)
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public String toString() {
    return "DecisionInstanceForListDto{"
        + "id='"
        + id
        + '\''
        + ", state="
        + state
        + ", decisionName='"
        + decisionName
        + '\''
        + ", decisionVersion="
        + decisionVersion
        + ", evaluationDate="
        + evaluationDate
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", sortValues="
        + Arrays.toString(sortValues)
        + '}';
  }
}
