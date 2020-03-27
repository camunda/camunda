package io.atomix.raft.primitive;

import com.google.common.collect.Sets;
import io.atomix.primitive.AbstractAsyncPrimitive;
import io.atomix.primitive.PrimitiveRegistry;
import io.atomix.primitive.SyncPrimitive;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TestPrimitiveImpl extends AbstractAsyncPrimitive<TestPrimitive, TestPrimitiveService>
    implements TestPrimitive, TestPrimitiveClient {

  private final Set<Consumer<Long>> eventListeners = Sets.newCopyOnWriteArraySet();
  private final Set<Consumer<String>> expireListeners = Sets.newCopyOnWriteArraySet();
  private final Set<Consumer<String>> closeListeners = Sets.newCopyOnWriteArraySet();

  public TestPrimitiveImpl(
      final ProxyClient<TestPrimitiveService> proxy, final PrimitiveRegistry registry) {
    super(proxy, registry);
  }

  @Override
  public PrimitiveProtocol protocol() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Long> write(final String value) {
    return getProxyClient().applyBy(name(), service -> service.write(value));
  }

  @Override
  public CompletableFuture<Long> read() {
    return getProxyClient().applyBy(name(), service -> service.read());
  }

  @Override
  public CompletableFuture<Long> sendEvent(final boolean sender) {
    return getProxyClient().applyBy(name(), service -> service.sendEvent(sender));
  }

  @Override
  public CompletableFuture<Void> onEvent(final Consumer<Long> callback) {
    eventListeners.add(callback);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onExpire(final Consumer<String> callback) {
    expireListeners.add(callback);
    return getProxyClient().acceptBy(name(), service -> service.onExpire());
  }

  @Override
  public CompletableFuture<Void> onClose(final Consumer<String> callback) {
    closeListeners.add(callback);
    return getProxyClient().acceptBy(name(), service -> service.onClose());
  }

  @Override
  public void event(final long index) {
    eventListeners.forEach(l -> l.accept(index));
  }

  @Override
  public void expire(final String value) {
    expireListeners.forEach(l -> l.accept(value));
  }

  @Override
  public void close(final String value) {
    closeListeners.forEach(l -> l.accept(value));
  }

  @Override
  public SyncPrimitive sync() {
    return null;
  }

  @Override
  public SyncPrimitive sync(final Duration operationTimeout) {
    return null;
  }
}
