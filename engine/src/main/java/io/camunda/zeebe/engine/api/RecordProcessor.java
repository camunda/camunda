/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

/**
 * Interface for record processors. A record processor is responsible for handling a single record.
 * (The class {@code StreamProcessor} in turn is responsible for handling a stream of records.
 */
public interface RecordProcessor {

  /**
   * Called by platform to initialize the processor
   *
   * @param recordProcessorContext context object to initialize the processor
   */
  void init(RecordProcessorContext recordProcessorContext);

  /**
   * Called by platform in order to replay a single record
   *
   * <p><em>Contract</em>
   *
   * <ul>
   *   <li>Record will be an event
   *   <li>Will be called before processing is called
   *   <li>Implementors can write to the database. Transaction is provided by platform, which also
   *       takes care of lifecycle of the transaction
   *   <li>Implementors must not write to the log stream
   *   <li>Implementors must not schedule post commit tasks
   * </ul>
   *
   * @param record the record to replay
   */
  void replay(TypedRecord record);

  /**
   * Called by platform to process a single record
   *
   * <p><em>Contract</em> * *
   *
   * <ul>
   *   *
   *   <li>Record will be a command
   *   <li>Will be called after replay is called
   *   <li>Implementors can write to the database. Transaction is provided by platform, which also *
   *       takes care of lifecycle of the transaction
   *   <li>Implementors must ensure that if they generate follow up events, these are applied to the
   *       database while this method is called
   *   <li>Implementors can produce follow up commands and events, client responses and on commit
   *       tasks via {@code * processingContext.getProcessingResultBuilder(). ... .build()}
   * </ul>
   *
   * @param record
   * @param processingContext
   * @return the result of the processing; must be generated via {@code
   *     processingContext.getProcessingResultBuilder().build()}
   */
  ProcessingResult process(TypedRecord record, ProcessingContext processingContext);

  /**
   * Called by platform when a processing error occurred
   *
   * @param processingException the exception that was thrown
   * @param record the record for which the exception was thrown
   * @param errorHandlingContext tbd
   * @return the result of the processing; must be generated via {@code *
   *     processingContext.getProcessingResultBuilder().build()}
   */
  ProcessingResult onProcessingError(
      Throwable processingException, TypedRecord record, ErrorHandlingContext errorHandlingContext);
}
