package io.camunda.debug.cli.log;

// Java equivalent of the Kotlin interface io.zell.zdb.log.records.PersistedRecord
public interface PersistedRecord {
  long index();

  long term();

  @Override
  String toString();

  String asColumnString();
}
