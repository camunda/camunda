/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * This service provides a few highly specific queries.
 *
 * <p>It exists to be used by the broker's temporary query api, which only exists as a temporary
 * solution to provide client authorization. Once client authorization is a first-class citizen,
 * this interface should be removed. We strongly discourage using this interface for anything else
 * than it's intended purpose.
 */
@Deprecated(forRemoval = true, since = "1.2")
public interface QueryService extends AutoCloseable {

  /**
   * Queries the state for the bpmn process id of a specific process.
   *
   * @param processKey The key of the process
   * @return Optionally the bpmn process id if found, otherwise an empty optional
   * @throws ClosedServiceException if the service is already closed
   */
  Optional<DirectBuffer> getBpmnProcessIdForProcess(long processKey);

  /**
   * Queries the state for the bpmn process id of the process of a specific process instance.
   *
   * @param processInstanceKey The key of the process instance
   * @return Optionally the bpmn process id if found, otherwise an empty optional
   * @throws ClosedServiceException if the service is already closed
   */
  Optional<DirectBuffer> getBpmnProcessIdForProcessInstance(long processInstanceKey);

  /**
   * Queries the state for the bpmn process id of the process that a specific job belongs to.
   *
   * @param jobKey The key of the job
   * @return Optionally the bpmn process id if found, otherwise an empty optional
   * @throws ClosedServiceException if the service is already closed
   */
  Optional<DirectBuffer> getBpmnProcessIdForJob(long jobKey);

  final class ClosedServiceException extends RuntimeException {}
}
