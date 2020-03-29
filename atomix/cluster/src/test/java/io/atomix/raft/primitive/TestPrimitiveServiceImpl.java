/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
