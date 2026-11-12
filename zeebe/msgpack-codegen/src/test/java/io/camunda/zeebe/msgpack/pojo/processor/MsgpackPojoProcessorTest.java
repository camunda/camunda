/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.pojo.Codecs;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/** Compiles small classes in memory with the processor and checks what it accepts and rejects. */
final class MsgpackPojoProcessorTest {

  private static final String IMPORTS =
      """
      package sample;
      import io.camunda.zeebe.msgpack.pojo.*;
      import java.util.*;
      import org.agrona.DirectBuffer;
      """;

  @Test
  void shouldRejectAPojoThatIsNotRecyclable() {
    // given
    final var source = pojo("@MsgpackPojo public final class Bad { long key; }");

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("must implement") && e.contains("Recyclable"));
  }

  @Test
  void shouldRejectAPrivateField() {
    // given
    final var source = pojo(valid("private long key;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("key") && e.contains("must not be private"));
  }

  @Test
  void shouldRejectAFinalScalarField() {
    // given
    final var source = pojo(valid("final long key = 1L;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("key") && e.contains("must not be final"));
  }

  @Test
  void shouldRejectAnUnsupportedFieldType() {
    // given
    final var source = pojo(valid("java.util.Map<String, String> key;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("Unsupported type") && e.contains("key"));
  }

  @Test
  void shouldRejectANestedTypeWithoutCodec() {
    // given
    final var source = pojo(valid("final StringBuilder key = new StringBuilder();"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("Unsupported type") && e.contains("key"));
  }

  @Test
  void shouldIgnoreFieldsMarkedAsIgnored() {
    // given a field of a type that would be rejected
    final var source = pojo(valid("long key; @MsgpackIgnore Thread ignored;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void shouldGenerateACodecThatRoundTripsAllSupportedTypes() throws Exception {
    // given
    final var result =
        compile(
            enumKind(),
            inner(),
            pojo(
                valid(
                    """
                    long key = -1L;
                    int count = 7;
                    boolean flag;
                    DirectBuffer name = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    @MsgpackProperty(binary = true) DirectBuffer payload = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    @MsgpackProperty("theKind") Kind kind;
                    final List<DirectBuffer> ids = new ArrayList<>();
                    final List<Long> versions = new ArrayList<>();
                    final Inner inner = new Inner();
                    @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                    """)));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    final var source = (Recyclable) sampleClass.getConstructor().newInstance();
    set(source, "key", 42L);
    set(source, "name", buffer("zeebe"));
    set(source, "flag", true);
    set(source, "payload", buffer("bin"));
    set(source, "kind", result.load("sample.Kind").getEnumConstants()[1]);
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("a"));
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("b"));
    ((List<Long>) get(source, "versions")).addAll(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    set(get(source, "inner"), "value", 9);

    // when
    @SuppressWarnings("unchecked")
    final MsgpackCodec<Recyclable> codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var bytes = new ExpandableArrayBuffer();
    final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), source);
    final var read = (Recyclable) sampleClass.getConstructor().newInstance();
    read.reset();
    codec.read(new MsgPackReader().wrap(bytes, 0, length), read);

    // then
    assertThat(length).isEqualTo(codec.getLength(source));
    assertThat(get(read, "key")).isEqualTo(42L);
    assertThat(get(read, "count")).isEqualTo(7);
    assertThat(get(read, "flag")).isEqualTo(true);
    assertThat(get(read, "name")).isEqualTo(buffer("zeebe"));
    assertThat(get(read, "payload")).isEqualTo(buffer("bin"));
    assertThat(bytes.byteArray())
        .as("the payload is stored as msgpack bin8, not as a string")
        .containsSequence((byte) 0xc4, (byte) 3, (byte) 'b', (byte) 'i', (byte) 'n');
    assertThat(get(read, "kind")).isEqualTo(get(source, "kind"));
    assertThat(get(read, "ids")).isEqualTo(List.of(buffer("a"), buffer("b")));
    assertThat(get(read, "versions")).isEqualTo(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    assertThat(get(get(read, "inner"), "value")).isEqualTo(9);
  }

  @Test
  void shouldGenerateAResetterThatRestoresTheFieldInitializers() throws Exception {
    // given
    final var result =
        compile(
            enumKind(),
            inner(),
            pojo(
                valid(
                    """
                    long key = -1L;
                    int count = 7;
                    DirectBuffer name = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    Kind kind;
                    final List<DirectBuffer> ids = new ArrayList<>();
                    final Inner inner = new Inner();
                    @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                    """)));
    assertThat(result.errors()).isEmpty();
    final var sample = (Recyclable) result.load("sample.Sample").getConstructor().newInstance();
    set(sample, "key", 5L);
    set(sample, "count", 1);
    set(sample, "name", buffer("dirty"));
    set(sample, "kind", result.load("sample.Kind").getEnumConstants()[0]);
    ((List<DirectBuffer>) get(sample, "ids")).add(buffer("a"));
    set(get(sample, "inner"), "value", 9);

    // when
    sample.reset();

    // then
    assertThat(get(sample, "key")).isEqualTo(-1L);
    assertThat(get(sample, "count")).isEqualTo(7);
    assertThat(get(sample, "name")).isEqualTo(buffer(""));
    assertThat(get(sample, "kind")).isNull();
    assertThat((List<?>) get(sample, "ids")).isEmpty();
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(3);
  }

  // ---------------------------------------------------------------- helpers

  private static String valid(final String body) {
    return """
    @MsgpackPojo
    public final class Sample extends MsgpackBufferValue<Sample> {
      %s
    }
    """
        .formatted(body);
  }

  private static Source pojo(final String classSource) {
    final var name = classSource.contains("class Bad") ? "Bad" : "Sample";
    return new Source("sample." + name, IMPORTS + classSource);
  }

  private static Source inner() {
    return new Source(
        "sample.Inner",
        IMPORTS
            + """
            @MsgpackPojo
            public final class Inner extends MsgpackBufferValue<Inner> {
              int value = 3;
              @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
            }
            """);
  }

  private static Source enumKind() {
    return new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }");
  }

  private static DirectBuffer buffer(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static void set(final Object target, final String field, final Object value)
      throws ReflectiveOperationException {
    final Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Object get(final Object target, final String field)
      throws ReflectiveOperationException {
    final Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }

  private static Result compile(final Source... sources) {
    final var compiler = ToolProvider.getSystemJavaCompiler();
    final var diagnostics = new DiagnosticCollector<JavaFileObject>();
    final var output = new HashMap<String, ByteArrayOutputStream>();
    final var fileManager =
        new ForwardingJavaFileManager<>(compiler.getStandardFileManager(diagnostics, null, null)) {
          @Override
          public JavaFileObject getJavaFileForOutput(
              final Location location,
              final String className,
              final JavaFileObject.Kind kind,
              final FileObject sibling) {
            return new SimpleJavaFileObject(
                URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
              @Override
              public OutputStream openOutputStream() {
                final var stream = new ByteArrayOutputStream();
                output.put(className, stream);
                return stream;
              }

              // javac reads the generated sources back
              @Override
              public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
                return output.get(className).toString(StandardCharsets.UTF_8);
              }
            };
          }
        };
    final var units = new ArrayList<JavaFileObject>();
    for (final var source : sources) {
      units.add(
          new SimpleJavaFileObject(
              URI.create("mem:///" + source.name().replace('.', '/') + ".java"),
              JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
              return source.code();
            }
          });
    }
    final var task =
        compiler.getTask(
            null,
            fileManager,
            diagnostics,
            List.of("-classpath", System.getProperty("java.class.path")),
            null,
            units);
    task.setProcessors(List.of(new MsgpackPojoProcessor()));
    task.call();
    final var errors = new ArrayList<String>();
    for (final var d : diagnostics.getDiagnostics()) {
      if (d.getKind() == Diagnostic.Kind.ERROR) {
        errors.add(d.getMessage(null));
      }
    }
    return new Result(errors, output);
  }

  private record Source(String name, String code) {}

  private record Result(
      List<String> errors, Map<String, ByteArrayOutputStream> output, ClassLoader loader) {
    Result(final List<String> errors, final Map<String, ByteArrayOutputStream> output) {
      this(errors, output, loader(output));
    }

    Class<?> load(final String className) throws ClassNotFoundException {
      return Class.forName(className, true, loader);
    }

    private static ClassLoader loader(final Map<String, ByteArrayOutputStream> output) {
      return new ClassLoader(MsgpackPojoProcessorTest.class.getClassLoader()) {
        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
          final var bytes = output.get(name);
          if (bytes == null) {
            throw new ClassNotFoundException(name);
          }
          final var data = bytes.toByteArray();
          return defineClass(name, data, 0, data.length);
        }
      };
    }
  }
}
