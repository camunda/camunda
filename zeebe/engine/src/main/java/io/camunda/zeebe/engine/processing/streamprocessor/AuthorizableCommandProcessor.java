/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableDistributionProcessor.Authorizable;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableDistributionProcessor.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * This processor decorates processors where authorization checks are required, taking care of the
 * authorization checks for them. If the authorization check fails, the processor will reject the
 * command and write a rejection response back to the client.
 *
 * <p>This decorator should be used for processors that do not require a resource from the state to
 * determine which resourceIds it is permitted for.
 */
public class AuthorizableCommandProcessor<T extends UnifiedRecordValue>
    implements CommandProcessor<T> {

  public static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  private final AuthorizationCheckBehavior authorizationCheckBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final Authorizable<T> delegate;

  public AuthorizableCommandProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final EngineConfiguration engineConfig,
      final Authorizable<T> delegate) {
    authorizationCheckBehavior =
        new AuthorizationCheckBehavior(
            processingState.getAuthorizationState(), processingState.getUserState(), engineConfig);
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.delegate = delegate;
  }

  @Override
  public boolean onCommand(final TypedRecord<T> command, final CommandControl<T> controller) {
    final var authorizationRequest = delegate.getAuthorizationRequest();

    if (authorizationCheckBehavior.isAuthorized(
        command, authorizationRequest.resourceType(), authorizationRequest.permissionType())) {
      return delegate.onCommand(command, controller);
    } else {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authorizationRequest.permissionType(), authorizationRequest.resourceType());
      controller.reject(RejectionType.UNAUTHORIZED, errorMessage);
    }

    return delegate.shouldRespond();
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final T value) {
    delegate.afterAccept(commandWriter, stateWriter, key, intent, value);
  }

  @Override
  public ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    return delegate.tryHandleError(command, error);
  }

  public record AuthorizationRequest(
      AuthorizationResourceType resourceType, PermissionType permissionType) {}

  public interface Authorizable<T extends UnifiedRecordValue> {
    AuthorizationRequest getAuthorizationRequest();

    boolean onCommand(final TypedRecord<T> command, CommandControl<T> controller);

    boolean shouldRespond();

    void afterAccept(
        final TypedCommandWriter commandWriter,
        final StateWriter stateWriter,
        final long key,
        final Intent intent,
        final T value);

    /**
     * Try to handle an error that occurred during processing.
     *
     * @param command The command that was being processed when the error occurred
     * @param error The error that occurred, and the processor should attempt to handle
     * @return The type of the processing error. Default: {@link ProcessingError#UNEXPECTED_ERROR}.
     */
    default ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
      return ProcessingError.UNEXPECTED_ERROR;
    }
  }
}
