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
package io.camunda.operate.webapp.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VariableDto {

  private String id;
  private String name;
  private String value;
  private boolean isPreview;
  private boolean hasActiveOperation = false;

  @Schema(description = "True when variable is the first in current list")
  private boolean isFirst = false;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  public static VariableDto createFrom(
      VariableEntity variableEntity,
      List<OperationEntity> operations,
      boolean fullValue,
      int variableSizeThreshold,
      ObjectMapper objectMapper) {
    if (variableEntity == null) {
      return null;
    }
    VariableDto variable = new VariableDto();
    variable.setId(variableEntity.getId());
    variable.setName(variableEntity.getName());

    if (fullValue) {
      if (variableEntity.getFullValue() != null) {
        variable.setValue(variableEntity.getFullValue());
      } else {
        variable.setValue(variableEntity.getValue());
      }
      variable.setIsPreview(false);
    } else {
      variable.setValue(variableEntity.getValue());
      variable.setIsPreview(variableEntity.getIsPreview());
    }

    if (CollectionUtil.isNotEmpty(operations)) {
      List<OperationEntity> activeOperations =
          CollectionUtil.filter(
              operations,
              (o ->
                  o.getState().equals(OperationState.SCHEDULED)
                      || o.getState().equals(OperationState.LOCKED)
                      || o.getState().equals(OperationState.SENT)));
      if (!activeOperations.isEmpty()) {
        variable.setHasActiveOperation(true);
        final String newValue =
            activeOperations.get(activeOperations.size() - 1).getVariableValue();
        if (fullValue) {
          variable.setValue(newValue);
        } else if (newValue.length() > variableSizeThreshold) {
          // set preview
          variable.setValue(newValue.substring(0, variableSizeThreshold));
          variable.setIsPreview(true);
        } else {
          variable.setValue(newValue);
        }
      }
    }

    // convert to String[]
    if (variableEntity.getSortValues() != null) {
      variable.setSortValues(
          SortValuesWrapper.createFrom(variableEntity.getSortValues(), objectMapper));
    }
    return variable;
  }

  public static List<VariableDto> createFrom(
      List<VariableEntity> variableEntities,
      Map<String, List<OperationEntity>> operations,
      int variableSizeThreshold,
      ObjectMapper objectMapper) {
    if (variableEntities == null) {
      return new ArrayList<>();
    }
    return variableEntities.stream()
        .filter(item -> item != null)
        .map(
            item ->
                createFrom(
                    item,
                    operations.get(item.getName()),
                    false,
                    variableSizeThreshold,
                    objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public VariableDto setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public void setHasActiveOperation(boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public VariableDto setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public VariableDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }
}
