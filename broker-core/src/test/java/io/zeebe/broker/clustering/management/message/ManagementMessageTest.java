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
package io.zeebe.broker.clustering.management.message;

import static io.zeebe.test.util.BufferWriterUtil.assertEqualFieldsAfterWriteAndRead;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.clustering.api.*;
import io.zeebe.clustering.management.ErrorResponseCode;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.junit.Test;

public class ManagementMessageTest {

  public static final DirectBuffer TOPIC_NAME = wrapString("test-topic");

  @Test
  public void testInvitationRequest() {
    final InvitationRequest invitationRequest =
        new InvitationRequest()
            .topicName(TOPIC_NAME)
            .partitionId(111)
            .term(222)
            .members(
                Arrays.asList(
                    new SocketAddress("localhost", 8001), new SocketAddress("localhost", 8002)));

    assertEqualFieldsAfterWriteAndRead(
        invitationRequest, "topicName", "partitionId", "replicationFactor", "term", "members");
  }

  @Test
  public void testInvitationResponse() {
    final InvitationResponse invitationResponse = new InvitationResponse().term(111);

    assertEqualFieldsAfterWriteAndRead(invitationResponse, "term");
  }

  @Test
  public void testListSnapshotsRequest() {
    final ListSnapshotsRequest request = new ListSnapshotsRequest().setPartitionId(111);
    assertEqualFieldsAfterWriteAndRead(request, "partitionId");
  }

  @Test
  public void testListSnapshotsResponse() {
    final String name = "test";
    final byte[] checksum = "abc".getBytes();
    final long length = 3L;
    final long position = 2L;
    final ListSnapshotsResponse response =
        new ListSnapshotsResponse().addSnapshot(name, position, checksum, length);

    assertEqualFieldsAfterWriteAndRead(response, "snapshots");
  }

  @Test
  public void testFetchSnapshotChunkRequest() {
    final FetchSnapshotChunkRequest request =
        new FetchSnapshotChunkRequest()
            .setPartitionId(1)
            .setName("snapshot")
            .setLogPosition(200L)
            .setChunkOffset(30)
            .setChunkLength(1024);

    assertEqualFieldsAfterWriteAndRead(
        request, "partitionId", "name", "logPosition", "chunkOffset", "chunkLength");
  }

  @Test
  public void testFetchSnapshotChunkResponse() {
    final DirectBuffer data = BufferUtil.wrapString("somethingOrOther");
    final FetchSnapshotChunkResponse response = new FetchSnapshotChunkResponse().setData(data);

    assertEqualFieldsAfterWriteAndRead(response, "data");
  }

  @Test
  public void testErrorResponse() {
    final ErrorResponseCode code = ErrorResponseCode.INVALID_PARAMETERS;
    final ErrorResponse response = new ErrorResponse().setCode(code).setData("invalid params");

    assertEqualFieldsAfterWriteAndRead(response, "code", "data");
  }
}
