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
package io.zeebe.engine.processor.workflow.deployment.model.transformation;

import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.traversal.TypeHierarchyVisitor;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.model.xml.type.ModelElementType;

public class TransformationVisitor extends TypeHierarchyVisitor {

  private final Map<Class<?>, ModelElementTransformer<?>> transformHandlers = new HashMap<>();

  private TransformContext context;

  public void setContext(TransformContext context) {
    this.context = context;
  }

  public TransformContext getContext() {
    return context;
  }

  public void registerHandler(ModelElementTransformer<?> transformHandler) {
    transformHandlers.put(transformHandler.getType(), transformHandler);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  protected void visit(ModelElementType implementedType, BpmnModelElementInstance instance) {
    final ModelElementTransformer handler =
        transformHandlers.get(implementedType.getInstanceType());

    if (handler != null) {
      handler.transform(instance, context);
    }
  }
}
