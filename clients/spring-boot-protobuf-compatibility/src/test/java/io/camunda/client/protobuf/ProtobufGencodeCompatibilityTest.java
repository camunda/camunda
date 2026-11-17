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
package io.camunda.client.protobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.protobuf.RuntimeVersion;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A protobuf runtime refuses gencode that is newer than itself. A user application gets its runtime
 * from Spring Boot, not from us, and thus it can get a runtime that is older than our gencode. This
 * module pins the lowest runtime that a user can get.
 */
final class ProtobufGencodeCompatibilityTest {

  /**
   * The protobuf-java version that the oldest Spring Boot line with protobuf management manages.
   * The pom holds the same version, and the Spring Boot BOM decides whether both are correct.
   */
  private static final String LOWEST_SUPPORTED_RUNTIME = "4.34.2";

  @Test
  void shouldRunOnTheLowestSupportedProtobufRuntime() throws Exception {
    // given, when javac inlines the version constants, thus read them reflectively to see the
    // runtime that is really on the classpath
    final String actual = version("MAJOR") + "." + version("MINOR") + "." + version("PATCH");

    // then
    assertThat(actual)
        .describedAs(
            "Raise this constant, the protobuf-java version of this module, and version.protoc "
                + "in parent/pom.xml together, and only when the oldest Spring Boot line that "
                + "manages protobuf changes")
        .isEqualTo(LOWEST_SUPPORTED_RUNTIME);
  }

  @Test
  void shouldPinTheRuntimeOfTheOldestSpringBootLineThatManagesProtobuf() throws Exception {
    // given the BOM of the oldest Spring Boot line that manages protobuf-java
    final String bom =
        new String(
            Objects.requireNonNull(
                    ProtobufGencodeCompatibilityTest.class.getResourceAsStream(
                        "/spring-boot-dependencies.pom"))
                .readAllBytes(),
            StandardCharsets.UTF_8);

    // when
    final Matcher managed =
        Pattern.compile("<protobuf-java\\.version>([^<]+)</protobuf-java\\.version>").matcher(bom);
    assertThat(managed.find()).isTrue();

    // then a raise of version.spring-boot-oldest-managing-protobuf reaches this module
    assertThat(managed.group(1))
        .describedAs(
            "Spring Boot changed the protobuf runtime of a user application. Raise "
                + "LOWEST_SUPPORTED_RUNTIME, the protobuf-java version of this module, and "
                + "version.protoc in parent/pom.xml to this version")
        .isEqualTo(LOWEST_SUPPORTED_RUNTIME);
  }

  private static int version(final String field) throws Exception {
    return RuntimeVersion.class.getField(field).getInt(null);
  }

  @Test
  void shouldCreateTheCommandsThatLoadGeneratedCode() {
    // given
    try (final CamundaClient client = CamundaClient.newClientBuilder().build()) {

      // when, then both commands load a generated class, and a user reaches both of them
      assertThatCode(() -> client.newActivateJobsCommand().jobType("test"))
          .doesNotThrowAnyException();
      assertThatCode(() -> client.newStreamJobsCommand().jobType("test"))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldLoadEveryGeneratedGatewayClass() {
    // given
    final List<Class<?>> generated = Arrays.asList(GatewayOuterClass.class.getDeclaredClasses());
    assertThat(generated).isNotEmpty();

    // when
    for (final Class<?> type : generated) {
      // then
      assertThatCode(() -> Class.forName(type.getName(), true, type.getClassLoader()))
          .describedAs(type.getSimpleName())
          .doesNotThrowAnyException();
    }
  }
}
