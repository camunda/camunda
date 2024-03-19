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
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.webapp.rest.dto.RequestValidator;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

@Schema(description = "Migration plan for process instance migration operation")
public class MigrationPlanDto implements RequestValidator {

  private String targetProcessDefinitionKey;
  private List<MappingInstruction> mappingInstructions;

  public String getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKey;
  }

  public MigrationPlanDto setTargetProcessDefinitionKey(String targetProcessDefinitionKey) {
    this.targetProcessDefinitionKey = targetProcessDefinitionKey;
    return this;
  }

  public List<MappingInstruction> getMappingInstructions() {
    return mappingInstructions;
  }

  public MigrationPlanDto setMappingInstructions(List<MappingInstruction> mappingInstructions) {
    this.mappingInstructions = mappingInstructions;
    return this;
  }

  @Override
  public void validate() {
    Long processDefinitionKey = null;
    try {
      final long key = Long.parseLong(targetProcessDefinitionKey);
      if (key > 0) {
        processDefinitionKey = key;
      }
    } catch (Exception ex) {
      // noop
    }
    if (processDefinitionKey == null) {
      throw new InvalidRequestException("Target process definition key must be a positive number.");
    }
    if (mappingInstructions == null || mappingInstructions.isEmpty()) {
      throw new InvalidRequestException("Mapping instructions are missing.");
    }
    final boolean hasNullMappings = mappingInstructions.stream().anyMatch(Objects::isNull);
    if (hasNullMappings) {
      throw new InvalidRequestException("Mapping instructions cannot be null.");
    }
    final boolean hasEmptyElements =
        mappingInstructions.stream()
            .anyMatch(
                x ->
                    StringUtils.isEmpty(x.getSourceElementId())
                        || StringUtils.isEmpty(x.getTargetElementId()));
    if (hasEmptyElements) {
      throw new InvalidRequestException("Mapping source and target elements cannot be empty.");
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetProcessDefinitionKey, mappingInstructions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MigrationPlanDto that = (MigrationPlanDto) o;
    return Objects.equals(targetProcessDefinitionKey, that.targetProcessDefinitionKey)
        && Objects.equals(mappingInstructions, that.mappingInstructions);
  }

  /** MappingInstruction */
  public static class MappingInstruction {

    private String sourceElementId;
    private String targetElementId;

    public String getSourceElementId() {
      return sourceElementId;
    }

    public MappingInstruction setSourceElementId(String sourceElementId) {
      this.sourceElementId = sourceElementId;
      return this;
    }

    public String getTargetElementId() {
      return targetElementId;
    }

    public MappingInstruction setTargetElementId(String targetElementId) {
      this.targetElementId = targetElementId;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourceElementId, targetElementId);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final MappingInstruction that = (MappingInstruction) o;
      return Objects.equals(sourceElementId, that.sourceElementId)
          && Objects.equals(targetElementId, that.targetElementId);
    }
  }
}
