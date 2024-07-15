package io.camunda.zeebe.protocol.record.intent;

public enum UserIntent implements Intent {
  CREATE(0),
  CREATED(1),
  UPDATE(2),
  UPDATING(3),
  UPDATED(4),
  DELETE(5),
  DELETING(6),
  DELETED(7);

  private final short value;

  UserIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case UPDATING:
      case UPDATED:
      case DELETING:
      case DELETED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return UPDATE;
      case 3:
        return UPDATING;
      case 4:
        return UPDATED;
      case 5:
        return DELETE;
      case 6:
        return DELETING;
      case 7:
        return DELETED;
      default:
        return UNKNOWN;
    }
  }
}
