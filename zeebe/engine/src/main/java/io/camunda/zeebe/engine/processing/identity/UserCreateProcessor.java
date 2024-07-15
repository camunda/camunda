package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UserCreateProcessor implements TypedRecordProcessor<UserRecord> {

  private final UserState userState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public UserCreateProcessor(final ProcessingState state, final Writers writers) {
    userState = state.getUserState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(TypedRecord<UserRecord> userRecord) {
    final long key = userRecord.getKey();
    final var username = userRecord.getValue().getUsernameBuffer();
    final var user = userState.getUser(username);

    if (user != null) {
      rejectionWriter.appendRejection(
          userRecord, RejectionType.ALREADY_EXISTS, "user already exists");
      responseWriter.writeRejectionOnCommand(
          userRecord, RejectionType.ALREADY_EXISTS, "user already exists");
      return;
    }

    stateWriter.appendFollowUpEvent(key, UserIntent.CREATED, userRecord.getValue());
    responseWriter.writeEventOnCommand(key, UserIntent.CREATED, userRecord.getValue(), userRecord);
  }
}
