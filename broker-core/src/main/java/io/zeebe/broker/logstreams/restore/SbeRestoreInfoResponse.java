/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.restore;

import io.zeebe.clustering.management.RestoreInfoResponseDecoder;
import io.zeebe.clustering.management.RestoreInfoResponseEncoder;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.RestoreStrategy.ReplicationTarget;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoResponse;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeRestoreInfoResponse
    extends SbeBufferWriterReader<RestoreInfoResponseEncoder, RestoreInfoResponseDecoder>
    implements RestoreInfoResponse {
  private static final ReplicationTarget[] ENUM_CONSTANTS =
      ReplicationTarget.class.getEnumConstants();

  private final RestoreInfoResponseEncoder encoder;
  private final RestoreInfoResponseDecoder decoder;
  private final DefaultRestoreInfoResponse delegate;

  public SbeRestoreInfoResponse() {
    this.delegate = new DefaultRestoreInfoResponse();
    this.encoder = new RestoreInfoResponseEncoder();
    this.decoder = new RestoreInfoResponseDecoder();
    reset();
  }

  public SbeRestoreInfoResponse(RestoreInfoResponse other) {
    this();
    setReplicationTarget(other.getReplicationTarget());
  }

  public SbeRestoreInfoResponse(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    setReplicationTarget(ENUM_CONSTANTS[decoder.replicationTarget()]);
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.replicationTarget((short) getReplicationTarget().ordinal());
  }

  @Override
  public ReplicationTarget getReplicationTarget() {
    return delegate.getReplicationTarget();
  }

  public void setReplicationTarget(ReplicationTarget replicationTarget) {
    delegate.setReplicationTarget(replicationTarget);
  }

  @Override
  protected RestoreInfoResponseEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected RestoreInfoResponseDecoder getBodyDecoder() {
    return decoder;
  }

  public static byte[] serialize(RestoreInfoResponse response) {
    return new SbeRestoreInfoResponse(response).toBytes();
  }
}
