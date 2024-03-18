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
package io.camunda.operate.webapp.rest.validation;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ModifyProcessInstanceRequestValidator {

  private final ProcessInstanceReader processInstanceReader;

  public ModifyProcessInstanceRequestValidator(final ProcessInstanceReader processInstanceReader) {
    this.processInstanceReader = processInstanceReader;
  }

  public void validate(final ModifyProcessInstanceRequestDto modifyRequest) {
    final Long processInstanceKey = Long.parseLong(modifyRequest.getProcessInstanceKey());
    if (processInstanceReader.getProcessInstanceByKey(processInstanceKey) == null) {
      throw new InvalidRequestException(
          String.format("Process instance with key %s does not exist", processInstanceKey));
    }
    if (!CollectionUtil.isNotEmpty(modifyRequest.getModifications())) {
      throw new InvalidRequestException(
          String.format(
              "No modifications given for process instance with key %s", processInstanceKey));
    }
    modifyRequest
        .getModifications()
        .forEach(
            modification -> {
              final ModifyProcessInstanceRequestDto.Modification.Type type =
                  modification.getModification();
              if (type == ModifyProcessInstanceRequestDto.Modification.Type.ADD_TOKEN) {
                validateAddToken(modification, processInstanceKey);
              } else if (type == ModifyProcessInstanceRequestDto.Modification.Type.CANCEL_TOKEN) {
                validateCancelToken(modification, processInstanceKey);
              } else if (type == ModifyProcessInstanceRequestDto.Modification.Type.MOVE_TOKEN) {
                validateMoveToken(modification, processInstanceKey);
              } else if (type == ModifyProcessInstanceRequestDto.Modification.Type.ADD_VARIABLE
                  || type == ModifyProcessInstanceRequestDto.Modification.Type.EDIT_VARIABLE) {
                validateAddEditVariable(modification, processInstanceKey);
              } else {
                throw new InvalidRequestException(
                    String.format(
                        "Unknown Modification.Type given for process instance with key %s.",
                        processInstanceKey));
              }
            });
  }

  private void validateAddEditVariable(
      final Modification modification, final Long processInstanceKey) {
    if (!MapUtils.isNotEmpty(modification.getVariables())) {
      throw new InvalidRequestException(
          String.format("No variables given for process instance with key %s", processInstanceKey));
    }
  }

  private void validateMoveToken(final Modification modification, final Long processInstanceKey) {
    validateAddToken(modification, processInstanceKey);
    validateCancelToken(modification, processInstanceKey);
  }

  private void validateCancelToken(final Modification modification, final Long processInstanceKey) {
    if (!StringUtils.hasText(modification.getFromFlowNodeId())
        && !StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
      throw new InvalidRequestException(
          String.format(
              "Neither fromFlowNodeId nor fromFlowNodeInstanceKey is given for process instance with key %s",
              processInstanceKey));
    }
    if (StringUtils.hasText(modification.getFromFlowNodeId())
        && StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
      throw new InvalidRequestException(
          String.format(
              "Either fromFlowNodeId or fromFlowNodeInstanceKey for process instance with key %s should be given, not both.",
              processInstanceKey));
    }
    if (modification.getFromFlowNodeInstanceKey() != null) {
      try {
        Long.parseLong(modification.getFromFlowNodeInstanceKey());
      } catch (final NumberFormatException nfe) {
        throw new InvalidRequestException("fromFlowNodeInstanceKey should be a Long.");
      }
    }
  }

  private void validateAddToken(final Modification modification, final Long processInstanceKey) {
    if (modification.getToFlowNodeId() == null) {
      throw new InvalidRequestException(
          String.format(
              "No toFlowNodeId given for process instance with key %s", processInstanceKey));
    }
  }
}
