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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class FallbackNamespaceTest {

  private static final String COMPAT_FIELD = "compatible";
  private static final String LEGACY_FIELD = "legacy";

  private final Namespace legacy = spy(Namespace.builder().register(TestClass.class).build());
  private final Namespace compatible =
      spy(Namespace.builder().register(TestClass.class).setCompatible(true).build());
  private final FallbackNamespace fallback = new FallbackNamespace(legacy, compatible);

  private final byte[] legacyBytes = legacy.serialize(new TestClass(LEGACY_FIELD));
  private final ByteBuffer legacyBuffer = ByteBuffer.wrap(legacyBytes);

  private final byte[] compatibleBytes = compatible.serialize(new TestClass(COMPAT_FIELD));
  private final ByteBuffer compatibleBuffer = ByteBuffer.wrap(compatibleBytes);

  @Before
  public void setup() {
    reset(compatible);
    reset(legacy);
  }

  @Test
  public void shouldDeserializeBytesWithCompatibleFirst() {
    // when
    final Object object = fallback.deserialize(compatibleBytes);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(COMPAT_FIELD);

    Mockito.verify(compatible, times(1)).deserialize(compatibleBytes);
    Mockito.verifyNoInteractions(legacy);
  }

  @Test
  public void shouldDeserializeBufferWithCompatibleFirst() {
    // when
    final Object object = fallback.deserialize(compatibleBuffer);

    // then
    assertThat(object).isInstanceOf(TestClass.class);
    assertThat(((TestClass) object).testField).isEqualTo(COMPAT_FIELD);

    Mockito.verify(compatible, times(1)).deserialize(compatibleBuffer);
    Mockito.verifyNoInteractions(legacy);
  }

  @Test
  public void shouldDeserializeBytesWithLegacyIfCompatibleFails() {
    // when
    final TestClass object = fallback.deserialize(legacyBytes);

    // then
    assertThat(object.testField).isEqualTo(LEGACY_FIELD);

    Mockito.verify(compatible, times(1)).deserialize(legacyBytes);
    Mockito.verify(legacy, times(1)).deserialize(legacyBytes);
  }

  @Test
  public void shouldDeserializeBufferWithLegacyIfCompatibleFails() {
    // when
    final TestClass object = fallback.deserialize(legacyBuffer);

    // then
    assertThat(object.testField).isEqualTo(LEGACY_FIELD);

    Mockito.verify(compatible, times(1)).deserialize(legacyBuffer);
    Mockito.verify(legacy, times(1)).deserialize(legacyBuffer);
  }

  @Test
  public void shouldSerializeWithCompatibleSerializer() {
    // when
    final TestClass original = new TestClass("test");
    final byte[] bytes = fallback.serialize(original);

    // then
    final TestClass deserialized = compatible.deserialize(bytes);
    assertThat(deserialized.testField).isEqualTo("test");

    Mockito.verify(compatible, times(1)).serialize(original);
    Mockito.verifyNoInteractions(legacy);
  }

  private static class TestClass {
    private final String testField;

    TestClass(final String testField) {
      this.testField = testField;
    }
  }
}
