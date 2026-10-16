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
package io.camunda.client.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

/**
 * Spring Boot 3.5 users get Micrometer 1.15 by default, while the starter is compiled against a
 * newer Micrometer. Tests run on that newer version too, so a call that only links on Micrometer
 * 1.16+ passes them and fails in users' apps with {@link NoSuchMethodError}. This test inspects the
 * compiled starter instead. See https://github.com/camunda/camunda/issues/59640
 */
class MicrometerSpringBoot3CompatibilityTest {

  private static final String MICROMETER_PACKAGE = "io/micrometer/";

  /** Methods added in Micrometer 1.16 that the starter must not call, as name + descriptor. */
  private static final Set<String> METHODS_MISSING_IN_MICROMETER_1_15 =
      Set.of(
          "counter(Ljava/lang/String;Lio/micrometer/core/instrument/Tags;)"
              + "Lio/micrometer/core/instrument/Counter;",
          "timer(Ljava/lang/String;Lio/micrometer/core/instrument/Tags;)"
              + "Lio/micrometer/core/instrument/Timer;");

  @Test
  void shouldNotCallMicrometerMethodsMissingFromSpringBoot35() throws Exception {
    // given
    final Path starterLocation =
        Path.of(
            MicrometerMetricsRecorder.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

    // when
    final List<String> incompatibleCalls = findIncompatibleCalls(starterLocation);

    // then
    assertThat(incompatibleCalls)
        .describedAs(
            "Calls to Micrometer methods that Spring Boot 3.5's Micrometer 1.15 lacks; pass tags as"
                + " Iterable<Tag> instead of Tags")
        .isEmpty();
  }

  private static List<String> findIncompatibleCalls(final Path starterLocation) throws IOException {
    if (Files.isDirectory(starterLocation)) {
      return findIncompatibleCallsIn(starterLocation);
    }
    try (final FileSystem jar = FileSystems.newFileSystem(starterLocation)) {
      return findIncompatibleCallsIn(jar.getPath("/"));
    }
  }

  private static List<String> findIncompatibleCallsIn(final Path root) throws IOException {
    final List<String> incompatibleCalls = new ArrayList<>();
    try (final Stream<Path> files = Files.walk(root.resolve("io/camunda/client"))) {
      for (final Path classFile :
          files.filter(file -> file.toString().endsWith(".class")).toList()) {
        try (final InputStream bytecode = Files.newInputStream(classFile)) {
          new ClassReader(bytecode)
              .accept(new IncompatibleCallCollector(incompatibleCalls), ClassReader.SKIP_DEBUG);
        }
      }
    }
    return incompatibleCalls;
  }

  private static final class IncompatibleCallCollector extends ClassVisitor {
    private final List<String> incompatibleCalls;
    private String className;

    private IncompatibleCallCollector(final List<String> incompatibleCalls) {
      super(Opcodes.ASM9);
      this.incompatibleCalls = incompatibleCalls;
    }

    @Override
    public void visit(
        final int version,
        final int access,
        final String name,
        final String signature,
        final String superName,
        final String[] interfaces) {
      className = name;
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String methodName,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public void visitMethodInsn(
            final int opcode,
            final String owner,
            final String name,
            final String calledDescriptor,
            final boolean isInterface) {
          if (owner.startsWith(MICROMETER_PACKAGE)
              && METHODS_MISSING_IN_MICROMETER_1_15.contains(name + calledDescriptor)) {
            incompatibleCalls.add(
                className + "#" + methodName + " calls " + owner + "#" + name + calledDescriptor);
          }
        }
      };
    }
  }
}
