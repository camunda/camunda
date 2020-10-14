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
package io.atomix.utils.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class FallbackNamespaceTest {

  private static final String COMPAT_FIELD = "compatible";
  private static final String LEGACY_FIELD = "legacy";

  private static final NamespaceImpl LEGACY =
      spy(new NamespaceImpl.Builder().register(TestClass.class).build());
  private static final NamespaceImpl COMPATIBLE =
      spy(new NamespaceImpl.Builder().register(TestClass.class).setCompatible(true).build());

  private static final byte[] COMPAT_BYTES = COMPATIBLE.serialize(new TestClass(COMPAT_FIELD));
  private static final ByteBuffer COMPAT_BUFFER = ByteBuffer.wrap(COMPAT_BYTES);
  private static final byte[] LEGACY_BYTES;
  private static final ByteBuffer LEGACY_BUFFER;

  static {
    final byte[] dataBytes = LEGACY.serialize(new TestClass(LEGACY_FIELD));
    LEGACY_BYTES = new byte[dataBytes.length - 2];
    System.arraycopy(dataBytes, 2, LEGACY_BYTES, 0, LEGACY_BYTES.length);

    LEGACY_BUFFER = ByteBuffer.wrap(LEGACY_BYTES);
  }

  private final FallbackNamespace fallback = new FallbackNamespace(LEGACY, COMPATIBLE);

  @Before
  public void setup() {
    COMPAT_BUFFER.clear();
    LEGACY_BUFFER.clear();

    reset(COMPATIBLE);
    reset(LEGACY);
  }

  @Test
  public void shouldDeserializeBytesAsNewWithV1Header() {
    // when
    final Object object = fallback.deserialize(COMPAT_BYTES);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(COMPAT_FIELD);

    Mockito.verify(COMPATIBLE, times(1)).deserialize(ArgumentMatchers.any(byte[].class), anyInt());
    Mockito.verifyNoInteractions(LEGACY);
  }

  @Test
  public void shouldDeserializeBufferAsNewWithV1Header() {
    // when
    final Object object = fallback.deserialize(COMPAT_BUFFER);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(COMPAT_FIELD);

    Mockito.verify(COMPATIBLE, times(1)).deserialize(ArgumentMatchers.any(ByteBuffer.class));
    Mockito.verifyNoInteractions(LEGACY);
  }

  @Test
  public void shouldDeserializeBytesAsOldWithoutHeader() {
    // when
    final Object object = fallback.deserialize(LEGACY_BYTES);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(LEGACY_FIELD);

    Mockito.verify(LEGACY, times(1)).deserialize(ArgumentMatchers.any(byte[].class));
    Mockito.verifyNoInteractions(COMPATIBLE);
  }

  @Test
  public void shouldDeserializeBufferAsOldWithoutHeader() {
    // when
    final Object object = fallback.deserialize(LEGACY_BUFFER);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(LEGACY_FIELD);

    Mockito.verify(LEGACY, times(1)).deserialize(ArgumentMatchers.any(ByteBuffer.class));
    Mockito.verifyNoInteractions(COMPATIBLE);
  }

  @Test
  public void shouldDeserializeBytesAsLegacyWithUnknownVersion() {
    // when
    final byte[] unknownVersion = {0x1F, (byte) 0xFF};
    assertThatThrownBy(() -> fallback.deserialize(unknownVersion));

    // then
    Mockito.verify(LEGACY, times(1)).deserialize(unknownVersion);
    Mockito.verifyNoInteractions(COMPATIBLE);
  }

  @Test
  public void shouldDeserializeBufferWithCompatibleFirst() {
    // when
    final Object object = fallback.deserialize(COMPAT_BUFFER);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(COMPAT_FIELD);

    Mockito.verify(COMPATIBLE, times(1)).deserialize(COMPAT_BUFFER);
    Mockito.verifyNoInteractions(LEGACY);
  }

  @Test
  public void shouldFallbackToLegacyIfDeserializingBytesFails() {
    // when
    final byte[] bytes = {0x01, (byte) 0xFF};
    assertThatThrownBy(() -> fallback.deserialize(bytes));

    // then
    Mockito.verify(COMPATIBLE, times(1)).deserialize(ArgumentMatchers.any(byte[].class), anyInt());
    Mockito.verify(LEGACY, times(1)).deserialize(bytes);
  }

  @Test
  public void shouldFallbackToLegacyIfDeserializingBufferFails() {
    // when
    final byte[] bytes = {0x01, (byte) 0xFF};
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    assertThatThrownBy(() -> fallback.deserialize(buffer));

    // then
    Mockito.verify(COMPATIBLE, times(1)).deserialize(buffer);
    Mockito.verify(LEGACY, times(1)).deserialize(buffer);
  }

  @Test
  public void shouldSerializeWithCompatibleSerializer() {
    // when
    final TestClass original = new TestClass("test");
    final byte[] bytes = fallback.serialize(original);

    // then
    final TestClass deserialized = fallback.deserialize(bytes);
    assertThat(deserialized.testField).isEqualTo("test");
    Mockito.verify(COMPATIBLE, times(1)).serialize(original);
  }

  @Test
  public void shouldSerializeWithVersionHeader() {
    // when
    final TestClass original = new TestClass("test");
    final byte[] bytes = fallback.serialize(original);

    // then
    assertThat(bytes).startsWith(NamespaceImpl.VERSION_HEADER);
  }

  private static class TestClass {
    private final String testField;

    TestClass(final String testField) {
      this.testField = testField;
    }
  }
}
