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

public abstract class OperateZeebeEntity extends OperateEntity {

  private long key;

  private int partitionId;

  public void setKey(long key) {
    this.key = key;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public long getKey() {
    return key;
  }

  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    OperateZeebeEntity that = (OperateZeebeEntity) o;

    if (key != that.key)
      return false;
    return partitionId == that.partitionId;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (int) (key ^ (key >>> 32));
    result = 31 * result + partitionId;
    return result;
  }
}
