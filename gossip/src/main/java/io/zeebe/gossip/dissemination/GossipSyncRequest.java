/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.gossip.dissemination;

import io.zeebe.util.collection.Reusable;
import io.zeebe.util.collection.ReusableObjectList;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class GossipSyncRequest implements Reusable {
  private final ReusableObjectList<GossipSyncResponsePart> parts =
      new ReusableObjectList<>(GossipSyncResponsePart::new);

  private final DirectBuffer type = new UnsafeBuffer(0, 0);

  public void wrap(DirectBuffer type) {
    this.type.wrap(type);
    this.parts.clear();
  }

  public GossipSyncRequest addPayload(int nodeId, DirectBuffer payload, int offset, int length) {
    parts.add().wrap(nodeId, payload, offset, length);

    return this;
  }

  public GossipSyncRequest addPayload(int nodeId, DirectBuffer payload) {
    parts.add().wrap(nodeId, payload);

    return this;
  }

  public DirectBuffer getType() {
    return type;
  }

  @Override
  public void reset() {
    type.wrap(0, 0);
  }

  public Iterable<GossipSyncResponsePart> getResponse() {
    return parts;
  }
}
