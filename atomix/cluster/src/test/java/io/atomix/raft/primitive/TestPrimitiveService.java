package io.atomix.raft.primitive;

import io.atomix.primitive.operation.Command;
import io.atomix.primitive.operation.Query;

/** Test primitive service interface. */
public interface TestPrimitiveService {

  @Command
  long write(String value);

  @Query
  long read();

  @Command
  long sendEvent(boolean sender);

  @Command
  void onExpire();

  @Command
  void onClose();
}
