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
package io.zeebe.broker.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.broker.clustering.orchestration.topic.PartitionsResponse;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class PartitionsResponseTest {

  @Test
  public void shouldEncodePartitions() throws Exception {
    // given
    final PartitionsResponse response = new PartitionsResponse();

    response.addPartition(1, BufferUtil.wrapString("default-topic"));
    response.addPartition(2, BufferUtil.wrapString("foo"));
    response.addPartition(3, BufferUtil.wrapString("foo"));

    // when
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[response.getLength()]);
    response.write(buf, 0);

    // then
    final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    final JsonNode deserializedResponse = objectMapper.readTree(new DirectBufferInputStream(buf));

    assertThat(deserializedResponse.isObject()).isTrue();
    assertThat(deserializedResponse.fieldNames()).containsExactly("partitions");

    final JsonNode partitions = deserializedResponse.get("partitions");
    assertThat(partitions.isArray()).isTrue();
    assertThat(partitions.size()).isEqualTo(3);

    final JsonNode partition3 = partitions.get(0);
    assertThat(partition3.isObject()).isTrue();
    assertThat(partition3.get("topic").textValue()).isEqualTo("default-topic");
    assertThat(partition3.get("id").numberValue()).isEqualTo(1);

    final JsonNode partition1 = partitions.get(1);
    assertThat(partition1.isObject()).isTrue();
    assertThat(partition1.get("topic").textValue()).isEqualTo("foo");
    assertThat(partition1.get("id").numberValue()).isEqualTo(2);

    final JsonNode partition2 = partitions.get(2);
    assertThat(partition2.isObject()).isTrue();
    assertThat(partition2.get("topic").textValue()).isEqualTo("foo");
    assertThat(partition2.get("id").numberValue()).isEqualTo(3);
  }
}
