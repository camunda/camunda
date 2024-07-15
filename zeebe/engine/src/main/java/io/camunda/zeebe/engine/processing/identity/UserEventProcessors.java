package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;

public final class UserEventProcessors {

  public static void addUserProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers) {

    typedRecordProcessors
        .onCommand(
            ValueType.USER, UserIntent.CREATE, new UserCreateProcessor(processingState, writers))
        .onCommand(ValueType.USER, UserIntent.UPDATE, new UserUpdateProcessor())
        .onCommand(ValueType.USER, UserIntent.DELETE, new UserDeleteProcessor());
  }
}
