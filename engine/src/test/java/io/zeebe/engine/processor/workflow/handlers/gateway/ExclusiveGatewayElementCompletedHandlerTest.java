/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.handlers.element.ElementHandlerTestCase;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.StoredRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ExclusiveGatewayElementCompletedHandlerTest
    extends ElementHandlerTestCase<ExecutableExclusiveGateway> {
  private ExclusiveGatewayElementCompletedHandler<ExecutableExclusiveGateway> handler;

  @Override
  public void setUp() {
    super.setUp();
    handler = new ExclusiveGatewayElementCompletedHandler<>();
  }

  @Test
  public void shouldPublishDeferredRecords() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final int currentTokens = flowScope.getNumberOfActiveTokens();
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED, flowScope);
    final StoredRecord deferred = deferRecordOn(instance);

    // when
    handler.handleState(context);

    // then
    Assertions.assertThat(flowScope.getNumberOfActiveTokens()).isEqualTo(currentTokens + 1);
    verifyRecordPublished(deferred, flowScope.getKey());
  }
}
