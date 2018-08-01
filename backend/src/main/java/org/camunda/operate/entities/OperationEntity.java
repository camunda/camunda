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
package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OperationEntity extends OperateEntity {

  private OperationType type;

  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private OffsetDateTime lockExpirationTime;
  private String lockOwner;

  private OperationState state;

  private String errorMessage;

  public OperationType getType() {
    return type;
  }

  public void setType(OperationType type) {
    this.type = type;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public OperationState getState() {
    return state;
  }

  public void setState(OperationState state) {
    this.state = state;
  }

  public OffsetDateTime getLockExpirationTime() {
    return lockExpirationTime;
  }

  public void setLockExpirationTime(OffsetDateTime lockExpirationTime) {
    this.lockExpirationTime = lockExpirationTime;
  }

  public String getLockOwner() {
    return lockOwner;
  }

  public void setLockOwner(String lockOwner) {
    this.lockOwner = lockOwner;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    OperationEntity that = (OperationEntity) o;

    if (type != that.type)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (lockExpirationTime != null ? !lockExpirationTime.equals(that.lockExpirationTime) : that.lockExpirationTime != null)
      return false;
    if (lockOwner != null ? !lockOwner.equals(that.lockOwner) : that.lockOwner != null)
      return false;
    if (state != that.state)
      return false;
    return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (lockExpirationTime != null ? lockExpirationTime.hashCode() : 0);
    result = 31 * result + (lockOwner != null ? lockOwner.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    return result;
  }
}
