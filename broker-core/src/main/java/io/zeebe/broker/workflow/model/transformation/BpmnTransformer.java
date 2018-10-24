/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.model.transformation;

import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.handler.ActivityHandler;
import io.zeebe.broker.workflow.model.transformation.handler.CreateWorkflowHandler;
import io.zeebe.broker.workflow.model.transformation.handler.EndEventHandler;
import io.zeebe.broker.workflow.model.transformation.handler.ExclusiveGatewayHandler;
import io.zeebe.broker.workflow.model.transformation.handler.FlowElementHandler;
import io.zeebe.broker.workflow.model.transformation.handler.FlowNodeHandler;
import io.zeebe.broker.workflow.model.transformation.handler.IntermediateCatchEventHandler;
import io.zeebe.broker.workflow.model.transformation.handler.MessageHandler;
import io.zeebe.broker.workflow.model.transformation.handler.ParallelGatewayHandler;
import io.zeebe.broker.workflow.model.transformation.handler.ProcessHandler;
import io.zeebe.broker.workflow.model.transformation.handler.ReceiveTaskHandler;
import io.zeebe.broker.workflow.model.transformation.handler.SequenceFlowHandler;
import io.zeebe.broker.workflow.model.transformation.handler.ServiceTaskHandler;
import io.zeebe.broker.workflow.model.transformation.handler.StartEventHandler;
import io.zeebe.broker.workflow.model.transformation.handler.SubProcessHandler;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import java.util.List;

public class BpmnTransformer {

  /*
   * Step 1: Instantiate all elements in the workflow
   */
  private final TransformationVisitor step1Visitor;

  /*
   * Step 2: Transform all attributes, cross-link elements, etc.
   */
  private final TransformationVisitor step2Visitor;

  private final JsonPathQueryCompiler jsonPathQueryCompiler = new JsonPathQueryCompiler();

  public BpmnTransformer() {
    this.step1Visitor = new TransformationVisitor();
    step1Visitor.registerHandler(new FlowElementHandler());
    step1Visitor.registerHandler(new CreateWorkflowHandler());
    step1Visitor.registerHandler(new MessageHandler());

    this.step2Visitor = new TransformationVisitor();
    step2Visitor.registerHandler(new ActivityHandler());
    step2Visitor.registerHandler(new EndEventHandler());
    step2Visitor.registerHandler(new ExclusiveGatewayHandler());
    step2Visitor.registerHandler(new FlowNodeHandler());
    step2Visitor.registerHandler(new IntermediateCatchEventHandler());
    step2Visitor.registerHandler(new ParallelGatewayHandler());
    step2Visitor.registerHandler(new ProcessHandler());
    step2Visitor.registerHandler(new SequenceFlowHandler());
    step2Visitor.registerHandler(new ServiceTaskHandler());
    step2Visitor.registerHandler(new ReceiveTaskHandler());
    step2Visitor.registerHandler(new StartEventHandler());
    step2Visitor.registerHandler(new SubProcessHandler());
  }

  public List<ExecutableWorkflow> transformDefinitions(BpmnModelInstance modelInstance) {
    final TransformContext context = new TransformContext();
    context.setJsonPathQueryCompiler(jsonPathQueryCompiler);

    final ModelWalker walker = new ModelWalker(modelInstance);
    step1Visitor.setContext(context);
    walker.walk(step1Visitor);

    step2Visitor.setContext(context);
    walker.walk(step2Visitor);

    return context.getWorkflows();
  }
}
