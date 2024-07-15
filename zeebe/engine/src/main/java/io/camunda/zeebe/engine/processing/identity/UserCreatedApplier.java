package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;

public class UserCreatedApplier implements TypedEventApplier<UserIntent, UserRecord> {

  private final MutableUserState userState;

  public UserCreatedApplier(final MutableProcessingState processingState) {
    userState = processingState.getUserState();
  }

  @Override
  public void applyState(final long key, final UserRecord value) {
    userState.create(value);
  }
}
