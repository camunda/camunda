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
package org.camunda.operate.entities.variables;

public class StringVariableEntity extends AbstractVariableEntity {

  private String value;

  public StringVariableEntity() {
  }

  public StringVariableEntity(String name, String value) {
    super(name);
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    StringVariableEntity that = (StringVariableEntity) o;

    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }
}
