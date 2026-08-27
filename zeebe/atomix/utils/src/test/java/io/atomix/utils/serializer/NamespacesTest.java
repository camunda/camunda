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

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import io.atomix.utils.serializer.Namespace.RegistrationBlock;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class NamespacesTest {

  /** Set from {@link Unregistered#readObject}, i.e. only if Java deserialization was reached. */
  private static final AtomicBoolean JAVA_DESERIALIZATION_REACHED = new AtomicBoolean();

  @BeforeEach
  void resetSink() {
    JAVA_DESERIALIZATION_REACHED.set(false);
  }

  @Test
  void shouldRoundTripMapEntry() {
    // given
    final var entry = new SimpleImmutableEntry<>("key", new ArrayList<>(List.of(1, 2, 3)));

    // when
    final Object decoded = Namespaces.BASIC.deserialize(Namespaces.BASIC.serialize(entry));

    // then
    assertThat(decoded).isEqualTo(entry);
  }

  @Test
  void shouldRejectUnregisteredTypeNestedInMapEntry() {
    // given
    final var entry = new SimpleImmutableEntry<>(new Unregistered(), "value");

    // when - then
    assertThatThrownBy(() -> Namespaces.BASIC.serialize(entry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not registered");
  }

  @Test
  void shouldNotJavaDeserializeIncomingMapEntry() {
    // given - bytes an attacker could put on the cluster transport, produced here by the
    // registration this namespace used before the fix
    final byte[] bytes =
        javaSerializingNamespace().serialize(new SimpleImmutableEntry<>(new Unregistered(), "v"));

    // when - then the payload is now read as Kryo rather than handed to ObjectInputStream, so it
    // fails to decode and, more importantly, never reaches Java deserialization
    assertThatThrownBy(() -> Namespaces.BASIC.deserialize(bytes)).isInstanceOf(KryoException.class);
    assertThat(JAVA_DESERIALIZATION_REACHED)
        .withFailMessage("Expected Java deserialization to never run while decoding a map entry")
        .isFalse();
  }

  @Test
  void shouldNotRegisterJavaSerializer() {
    // given - when
    final List<Serializer<?>> serializers =
        Namespaces.BASIC.getRegisteredBlocks().stream()
            .flatMap(block -> block.types().stream())
            .map(Pair::getRight)
            .filter(Objects::nonNull)
            .toList();

    // then - a JavaSerializer delegates to ObjectInputStream, which ignores the Kryo registration
    // allowlist and so reconstructs any type nested in the value it deserializes
    assertThat(serializers).doesNotHaveAnyElementsOfTypes(JavaSerializer.class);
  }

  /**
   * {@link Namespaces#BASIC} with {@link SimpleImmutableEntry} registered through a {@link
   * JavaSerializer} again, so that it produces the same bytes a pre-fix broker would. Copying the
   * registration blocks keeps every class id identical, so {@link Namespaces#BASIC} recognises the
   * entry and attempts to decode it — the point of the test is what happens next, not that the
   * decode succeeds.
   */
  private static Namespace javaSerializingNamespace() {
    final List<RegistrationBlock> blocks =
        Namespaces.BASIC.getRegisteredBlocks().stream()
            .map(
                block ->
                    new RegistrationBlock(
                        block.begin(),
                        block.types().stream()
                            .map(NamespacesTest::withJavaSerializerForMapEntry)
                            .toList()))
            .toList();
    return new Namespace(blocks, "JAVA_SERIALIZING");
  }

  private static Pair<Class<?>[], Serializer<?>> withJavaSerializerForMapEntry(
      final Pair<Class<?>[], Serializer<?>> registration) {
    if (List.of(registration.getLeft()).contains(SimpleImmutableEntry.class)) {
      return Pair.of(registration.getLeft(), new JavaSerializer());
    }
    return registration;
  }

  /** Deliberately not registered with any namespace, and observably read if it ever is. */
  private static final class Unregistered implements Serializable {
    private final String tag = "unregistered";

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      JAVA_DESERIALIZATION_REACHED.set(true);
    }
  }
}
