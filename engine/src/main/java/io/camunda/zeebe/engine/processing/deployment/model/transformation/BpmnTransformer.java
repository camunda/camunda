/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformation;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.transformer.BoundaryEventTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.CallActivityTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.CatchEventTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ContextProcessTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.EndEventTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ErrorTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.EventBasedGatewayTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ExclusiveGatewayTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.FlowElementInstantiationTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.FlowNodeTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.IntermediateCatchEventTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.MessageTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.MultiInstanceActivityTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ProcessTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ReceiveTaskTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.SequenceFlowTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.ServiceTaskTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.StartEventTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.SubProcessTransformer;
import io.zeebe.engine.processing.deployment.model.transformer.UserTaskTransformer;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import java.util.List;

public final class BpmnTransformer {

  /*
   * Step 1: Instantiate all elements in the process
   */
  private final TransformationVisitor step1Visitor;

  /*
   * Step 2: Transform all attributes, cross-link elements, etc.
   */
  private final TransformationVisitor step2Visitor;

  /*
   * Step 3: Modify elements based on the context
   */
  private final TransformationVisitor step3Visitor;

  /*
   * Step 4: Modify elements based on containing elements
   */
  private final TransformationVisitor step4Visitor;

  private final ExpressionLanguage expressionLanguage;

  public BpmnTransformer(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;

    step1Visitor = new TransformationVisitor();
    step1Visitor.registerHandler(new ErrorTransformer());
    step1Visitor.registerHandler(new FlowElementInstantiationTransformer());
    step1Visitor.registerHandler(new MessageTransformer());
    step1Visitor.registerHandler(new ProcessTransformer());

    step2Visitor = new TransformationVisitor();
    step2Visitor.registerHandler(new BoundaryEventTransformer());
    step2Visitor.registerHandler(new CallActivityTransformer());
    step2Visitor.registerHandler(new CatchEventTransformer());
    step2Visitor.registerHandler(new ContextProcessTransformer());
    step2Visitor.registerHandler(new EndEventTransformer());
    step2Visitor.registerHandler(new FlowNodeTransformer());
    step2Visitor.registerHandler(new SequenceFlowTransformer());
    step2Visitor.registerHandler(new ServiceTaskTransformer());
    step2Visitor.registerHandler(new ReceiveTaskTransformer());
    step2Visitor.registerHandler(new UserTaskTransformer());
    step2Visitor.registerHandler(new StartEventTransformer());

    step3Visitor = new TransformationVisitor();
    step3Visitor.registerHandler(new ContextProcessTransformer());
    step3Visitor.registerHandler(new EventBasedGatewayTransformer());
    step3Visitor.registerHandler(new ExclusiveGatewayTransformer());
    step3Visitor.registerHandler(new IntermediateCatchEventTransformer());
    step3Visitor.registerHandler(new SubProcessTransformer());

    step4Visitor = new TransformationVisitor();
    step4Visitor.registerHandler(new MultiInstanceActivityTransformer());
  }

  public List<ExecutableProcess> transformDefinitions(final BpmnModelInstance modelInstance) {
    final TransformContext context = new TransformContext();
    context.setExpressionLanguage(expressionLanguage);

    final ModelWalker walker = new ModelWalker(modelInstance);
    step1Visitor.setContext(context);
    walker.walk(step1Visitor);

    step2Visitor.setContext(context);
    walker.walk(step2Visitor);

    step3Visitor.setContext(context);
    walker.walk(step3Visitor);

    step4Visitor.setContext(context);
    walker.walk(step4Visitor);

    return context.getProcesses();
  }
}
