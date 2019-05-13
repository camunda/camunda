/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.deployment.transform;

import io.zeebe.model.bpmn.instance.BaseElement;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultFormatter;

public class ValidationErrorFormatter implements ValidationResultFormatter {

  @Override
  public void formatElement(StringWriter writer, ModelElementInstance element) {
    writer.append("- Element: ");
    writer.append(createElementIdentifier(element));
    writer.append("\n");
  }

  @Override
  public void formatResult(StringWriter writer, ValidationResult result) {
    writer.append("    - ");
    writer.append(result.getType().toString());
    writer.append(": ");
    writer.append(result.getMessage());
    writer.append("\n");
  }

  /**
   * Build an identifier starting with the closest parent element that has an id.
   *
   * <p>E.g. a service task has a task definition with a validation error, then the identifier
   * should be: <code>taskId > extensionElements > taskDefinition</code>
   */
  private String createElementIdentifier(ModelElementInstance element) {
    final List<ModelElementInstance> identifiableElementChain = new ArrayList<>();

    ModelElementInstance current = element;
    do {
      identifiableElementChain.add(0, current);
      if (current instanceof BaseElement && ((BaseElement) current).getId() != null) {
        current = null;
      } else {
        current = current.getParentElement();
      }
    } while (current != null);

    final StringBuilder sb = new StringBuilder();

    for (int i = 0; i < identifiableElementChain.size(); i++) {
      final ModelElementInstance chainElement = identifiableElementChain.get(i);
      if (chainElement instanceof BaseElement && ((BaseElement) chainElement).getId() != null) {
        sb.append(((BaseElement) chainElement).getId());
      } else {
        sb.append(chainElement.getElementType().getTypeName());
      }

      if (i < identifiableElementChain.size() - 1) {
        sb.append(" > ");
      }
    }

    return sb.toString();
  }
}
