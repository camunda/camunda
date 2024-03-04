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
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Decision object")
public class DecisionDto implements CreatableFromEntity<DecisionDto, DecisionDefinitionEntity> {

  @Schema(
      description =
          "Unique id of the decision, must be used when filtering instances by decision ids.")
  private String id;

  private String name;
  private int version;
  private String decisionId;

  public String getId() {
    return id;
  }

  public DecisionDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionDto setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public DecisionDto setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionDto setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  @Override
  public DecisionDto fillFrom(final DecisionDefinitionEntity decisionEntity) {
    return this.setId(decisionEntity.getId())
        .setDecisionId(decisionEntity.getDecisionId())
        .setName(decisionEntity.getName())
        .setVersion(decisionEntity.getVersion());
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (decisionId != null ? decisionId.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DecisionDto that = (DecisionDto) o;

    if (version != that.version) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    return decisionId != null ? decisionId.equals(that.decisionId) : that.decisionId == null;
  }
}
