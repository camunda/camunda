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

import static io.zeebe.clustering.management.RestoreInfoRequestEncoder.backupPositionNullValue;
import static io.zeebe.clustering.management.RestoreInfoRequestEncoder.latestLocalPositionNullValue;

import io.zeebe.clustering.management.RestoreInfoRequestDecoder;
import io.zeebe.clustering.management.RestoreInfoRequestEncoder;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoRequest;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class SbeRestoreInfoRequest
    extends SbeBufferWriterReader<RestoreInfoRequestEncoder, RestoreInfoRequestDecoder>
    implements RestoreInfoRequest {
  private final RestoreInfoRequestDecoder decoder;
  private final RestoreInfoRequestEncoder encoder;

  private final DefaultRestoreInfoRequest delegate;

  public SbeRestoreInfoRequest() {
    this.delegate = new DefaultRestoreInfoRequest();
    this.encoder = new RestoreInfoRequestEncoder();
    this.decoder = new RestoreInfoRequestDecoder();
    reset();
  }

  public SbeRestoreInfoRequest(RestoreInfoRequest other) {
    this();
    setBackupPosition(other.getBackupPosition());
    setLatestLocalPosition(other.getLatestLocalPosition());
  }

  public SbeRestoreInfoRequest(byte[] serialized) {
    this();
    wrap(new UnsafeBuffer(serialized));
  }

  @Override
  public void reset() {
    super.reset();
    setLatestLocalPosition(latestLocalPositionNullValue());
    setBackupPosition(backupPositionNullValue());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    setLatestLocalPosition(decoder.latestLocalPosition());
    setBackupPosition(decoder.backupPosition());
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);
    encoder.backupPosition(getBackupPosition());
    encoder.latestLocalPosition(getLatestLocalPosition());
  }

  @Override
  public long getLatestLocalPosition() {
    return delegate.getLatestLocalPosition();
  }

  public void setLatestLocalPosition(long latestLocalPosition) {
    delegate.setLatestLocalPosition(latestLocalPosition);
  }

  @Override
  public long getBackupPosition() {
    return delegate.getBackupPosition();
  }

  public void setBackupPosition(long backupPosition) {
    delegate.setBackupPosition(backupPosition);
  }

  public static byte[] serialize(RestoreInfoRequest request) {
    return new SbeRestoreInfoRequest(request).toBytes();
  }

  @Override
  protected RestoreInfoRequestEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected RestoreInfoRequestDecoder getBodyDecoder() {
    return decoder;
  }
}
