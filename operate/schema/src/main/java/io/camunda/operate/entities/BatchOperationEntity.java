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
package io.camunda.operate.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.UUID;

public class BatchOperationEntity extends OperateEntity<BatchOperationEntity> {

  private String name;
  private OperationType type;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String username;

  private Integer instancesCount = 0;
  private Integer operationsTotalCount = 0;
  private Integer operationsFinishedCount = 0;

  @JsonIgnore private Object[] sortValues;

  public String getName() {
    return name;
  }

  public BatchOperationEntity setName(String name) {
    this.name = name;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public BatchOperationEntity setType(OperationType type) {
    this.type = type;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public BatchOperationEntity setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public BatchOperationEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public BatchOperationEntity setUsername(String username) {
    this.username = username;
    return this;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public BatchOperationEntity setInstancesCount(Integer instancesCount) {
    this.instancesCount = instancesCount;
    return this;
  }

  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  public BatchOperationEntity setOperationsTotalCount(Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
    return this;
  }

  public Integer getOperationsFinishedCount() {
    return operationsFinishedCount;
  }

  public BatchOperationEntity setOperationsFinishedCount(Integer operationsFinishedCount) {
    this.operationsFinishedCount = operationsFinishedCount;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public BatchOperationEntity setSortValues(Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (username != null ? username.hashCode() : 0);
    result = 31 * result + (instancesCount != null ? instancesCount.hashCode() : 0);
    result = 31 * result + (operationsTotalCount != null ? operationsTotalCount.hashCode() : 0);
    result =
        31 * result + (operationsFinishedCount != null ? operationsFinishedCount.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    BatchOperationEntity that = (BatchOperationEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    if (type != that.type) return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) return false;
    if (username != null ? !username.equals(that.username) : that.username != null) return false;
    if (instancesCount != null
        ? !instancesCount.equals(that.instancesCount)
        : that.instancesCount != null) return false;
    if (operationsTotalCount != null
        ? !operationsTotalCount.equals(that.operationsTotalCount)
        : that.operationsTotalCount != null) return false;
    return operationsFinishedCount != null
        ? operationsFinishedCount.equals(that.operationsFinishedCount)
        : that.operationsFinishedCount == null;
  }
}
