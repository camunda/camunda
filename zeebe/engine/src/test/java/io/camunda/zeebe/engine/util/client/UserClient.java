package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;

public final class UserClient {

  private final UserRecord userRecord;
  private final CommandWriter writer;

  public UserClient(final CommandWriter writer) {
    this.writer = writer;
    userRecord = new UserRecord();
  }

  public UserCreationClient newUser(final String username) {
    return new UserCreationClient(writer, username);
  }

  public static class UserCreationClient {

    private final CommandWriter writer;
    private final UserRecord userCreationRecord;

    public UserCreationClient(final CommandWriter writer, final String username) {
      this.writer = writer;
      userCreationRecord = new UserRecord();
      userCreationRecord.setUsername(username);
    }

    public UserCreationClient withName(final String name) {
      userCreationRecord.setName(name);
      return this;
    }

    public UserCreationClient withEmail(final String email) {
      userCreationRecord.setEmail(email);
      return this;
    }

    public UserRecord create() {
      final long position = writer.writeCommand(UserIntent.CREATE, userCreationRecord);
      return null;
    }
  }
}
