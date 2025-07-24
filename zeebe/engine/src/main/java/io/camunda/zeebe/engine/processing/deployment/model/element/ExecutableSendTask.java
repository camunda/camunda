/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

/*
 * Placeholder model for the executable BPMN Send Task.
 *
 * Currently, Send Tasks in Zeebe only support job worker-based implementations.
 * This class extends ExecutableJobWorkerTask to expose job worker properties
 * required for the supported use case.
 *
 * Once support for additional implementations (e.g., publish message-based) is introduced,
 * this class should be updated to support those properties directly
 * (similar to ExecutableScriptTask or ExecutableBusinessRuleTask).
 */
public final class ExecutableSendTask extends ExecutableJobWorkerTask {

  public ExecutableSendTask(final String id) {
    super(id);
  }
}
