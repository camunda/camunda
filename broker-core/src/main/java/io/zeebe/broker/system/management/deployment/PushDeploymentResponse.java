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
package io.zeebe.broker.system.management.deployment;

import io.zeebe.clustering.management.PushDeploymentRequestEncoder;
import io.zeebe.clustering.management.PushDeploymentResponseDecoder;
import io.zeebe.clustering.management.PushDeploymentResponseEncoder;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PushDeploymentResponse
    extends SbeBufferWriterReader<PushDeploymentResponseEncoder, PushDeploymentResponseDecoder> {

  private final PushDeploymentResponseEncoder bodyEncoder = new PushDeploymentResponseEncoder();
  private final PushDeploymentResponseDecoder bodyDecoder = new PushDeploymentResponseDecoder();

  private int partitionId = PushDeploymentResponseEncoder.partitionIdNullValue();
  private long deploymentKey = PushDeploymentResponseEncoder.deploymentKeyNullValue();

  public PushDeploymentResponse partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public int partitionId() {
    return this.partitionId;
  }

  public PushDeploymentResponse deploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public long deploymentKey() {
    return this.deploymentKey;
  }

  @Override
  protected PushDeploymentResponseEncoder getBodyEncoder() {
    return bodyEncoder;
  }

  @Override
  protected PushDeploymentResponseDecoder getBodyDecoder() {
    return bodyDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    partitionId = bodyDecoder.partitionId();
    deploymentKey = bodyDecoder.deploymentKey();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    bodyEncoder.partitionId(partitionId).deploymentKey(deploymentKey);
  }

  public void reset() {
    super.reset();

    partitionId = PushDeploymentResponseEncoder.partitionIdNullValue();
    deploymentKey = PushDeploymentRequestEncoder.deploymentKeyNullValue();
  }
}
