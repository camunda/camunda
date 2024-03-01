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
package io.camunda.operate.webapp.rest.dto.dmn.list;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceListQueryDto {

  private List<String> decisionDefinitionIds;

  private boolean evaluated;
  private boolean failed;

  private List<String> ids;
  private String processInstanceId;

  @Schema(description = "Evaluation date after (inclusive)", nullable = true)
  private OffsetDateTime evaluationDateAfter;

  @Schema(description = "Evaluation date after (inclusive)", nullable = true)
  private OffsetDateTime evaluationDateBefore;

  private String tenantId;

  public List<String> getDecisionDefinitionIds() {
    return decisionDefinitionIds;
  }

  public DecisionInstanceListQueryDto setDecisionDefinitionIds(
      final List<String> decisionDefinitionIds) {
    this.decisionDefinitionIds = decisionDefinitionIds;
    return this;
  }

  public boolean isEvaluated() {
    return evaluated;
  }

  public DecisionInstanceListQueryDto setEvaluated(final boolean evaluated) {
    this.evaluated = evaluated;
    return this;
  }

  public boolean isFailed() {
    return failed;
  }

  public DecisionInstanceListQueryDto setFailed(final boolean failed) {
    this.failed = failed;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public DecisionInstanceListQueryDto setIds(final List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceListQueryDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getEvaluationDateAfter() {
    return evaluationDateAfter;
  }

  public DecisionInstanceListQueryDto setEvaluationDateAfter(
      final OffsetDateTime evaluationDateAfter) {
    this.evaluationDateAfter = evaluationDateAfter;
    return this;
  }

  public OffsetDateTime getEvaluationDateBefore() {
    return evaluationDateBefore;
  }

  public DecisionInstanceListQueryDto setEvaluationDateBefore(
      final OffsetDateTime evaluationDateBefore) {
    this.evaluationDateBefore = evaluationDateBefore;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceListQueryDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        decisionDefinitionIds,
        evaluated,
        failed,
        ids,
        processInstanceId,
        evaluationDateAfter,
        evaluationDateBefore,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionInstanceListQueryDto that = (DecisionInstanceListQueryDto) o;
    return evaluated == that.evaluated
        && failed == that.failed
        && Objects.equals(decisionDefinitionIds, that.decisionDefinitionIds)
        && Objects.equals(ids, that.ids)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(evaluationDateAfter, that.evaluationDateAfter)
        && Objects.equals(evaluationDateBefore, that.evaluationDateBefore)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionInstanceListQueryDto{"
        + "decisionDefinitionIds="
        + decisionDefinitionIds
        + ", evaluated="
        + evaluated
        + ", failed="
        + failed
        + ", ids="
        + ids
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", evaluationDateAfter="
        + evaluationDateAfter
        + ", evaluationDateBefore="
        + evaluationDateBefore
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
