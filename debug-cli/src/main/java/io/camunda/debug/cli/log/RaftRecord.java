package io.camunda.debug.cli.log;

public class RaftRecord implements PersistedRecord {
  private final long index;
  private final long term;

  public RaftRecord(final long index, final long term) {
    this.index = index;
    this.term = term;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public String asColumnString() {
    return index + " " + term + " ";
  }

  @Override
  public String toString() {
    return String.format("{\"index\":%d,\"term\":%d}", index, term);
  }
}
