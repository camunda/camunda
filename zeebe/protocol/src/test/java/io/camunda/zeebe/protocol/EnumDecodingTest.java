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
package io.camunda.zeebe.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ErrorResponseDecoder;
import io.camunda.zeebe.protocol.record.ErrorResponseEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class EnumDecodingTest {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

  /**
   * This case is important for forward compatibility. Assume client in version X, broker in version
   * Y, X < Y. The broker may send the client a response with a higher protocol version. In case of
   * an error response, this can contain enum values (error code) that the client does not know.
   * This should not result in an exception.
   */
  @Test
  void shouldHandleUnknownEnumValue() {
    // given
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
    headerEncoder
        .wrap(buffer, 0)
        .blockLength(bodyEncoder.sbeBlockLength())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion() + 1) // assuming a newer protocol version
        .templateId(bodyEncoder.sbeTemplateId());

    bodyEncoder
        .wrap(buffer, headerEncoder.encodedLength())
        .errorCode(ErrorCode.INVALID_CLIENT_VERSION)
        .errorData("foo");

    final byte unknownEnumValue = Byte.MAX_VALUE;
    buffer.putByte(
        headerEncoder.encodedLength() + ErrorResponseEncoder.errorCodeEncodingOffset(),
        unknownEnumValue);

    headerDecoder.wrap(buffer, 0);

    // when
    bodyDecoder.wrap(
        buffer,
        headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    // then
    assertThat(bodyDecoder.errorCode()).isEqualByComparingTo(ErrorCode.SBE_UNKNOWN);
  }
}
