package io.atomix.raft.primitive;

import io.atomix.primitive.AsyncPrimitive;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test primitive. */
public interface TestPrimitive extends AsyncPrimitive {

  CompletableFuture<Long> write(String value);

  CompletableFuture<Long> read();

  CompletableFuture<Long> sendEvent(boolean sender);

  CompletableFuture<Void> onEvent(Consumer<Long> callback);

  CompletableFuture<Void> onExpire(Consumer<String> callback);

  CompletableFuture<Void> onClose(Consumer<String> callback);
}
