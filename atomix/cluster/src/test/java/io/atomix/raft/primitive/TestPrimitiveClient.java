package io.atomix.raft.primitive;

import io.atomix.primitive.event.Event;

/** Test primitive client interface. */
public interface TestPrimitiveClient {

  @Event("event")
  void event(long index);

  @Event("expire")
  void expire(String value);

  @Event("close")
  void close(String value);
}
