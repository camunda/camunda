package io.atomix.raft.primitive;

import static org.junit.Assert.assertEquals;

import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.raft.RaftTest;
import io.atomix.utils.serializer.Serializer;

/** Test state machine. */
public class TestPrimitiveServiceImpl extends AbstractPrimitiveService<TestPrimitiveClient>
    implements TestPrimitiveService {

  private SessionId expire;
  private SessionId close;

  public TestPrimitiveServiceImpl(final ServiceConfig config) {
    super(TestPrimitiveType.INSTANCE, TestPrimitiveClient.class);
  }

  @Override
  public Serializer serializer() {
    return Serializer.using(TestPrimitiveType.INSTANCE.namespace());
  }

  @Override
  public void onExpire(final Session session) {
    if (expire != null) {
      getSession(expire).accept(client -> client.expire("Hello world!"));
    }
  }

  @Override
  public void onClose(final Session session) {
    if (close != null && !session.sessionId().equals(close)) {
      getSession(close).accept(client -> client.close("Hello world!"));
    }
  }

  @Override
  public void backup(final BackupOutput writer) {
    RaftTest.snapshots.incrementAndGet();
    writer.writeLong(10);
  }

  @Override
  public void restore(final BackupInput reader) {
    assertEquals(10, reader.readLong());
  }

  @Override
  public long write(final String value) {
    return getCurrentIndex();
  }

  @Override
  public long read() {
    return getCurrentIndex();
  }

  @Override
  public long sendEvent(final boolean sender) {
    if (sender) {
      getCurrentSession().accept(service -> service.event(getCurrentIndex()));
    } else {
      for (final Session<TestPrimitiveClient> session : getSessions()) {
        session.accept(service -> service.event(getCurrentIndex()));
      }
    }
    return getCurrentIndex();
  }

  @Override
  public void onExpire() {
    expire = getCurrentSession().sessionId();
  }

  @Override
  public void onClose() {
    close = getCurrentSession().sessionId();
  }
}
