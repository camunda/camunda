/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Compiles sources in memory with the {@link MsgpackPojoProcessor} and loads the result. */
final class InMemoryCompiler {

  private InMemoryCompiler() {}

  static void set(final Object target, final String field, final Object value)
      throws ReflectiveOperationException {
    final Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  static Object get(final Object target, final String field) throws ReflectiveOperationException {
    final Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }

  static Result compile(final Source... sources) {
    return compile(null, sources);
  }

  /** Like {@link #compile(Source...)}, with the classes of a previous compilation on the path. */
  static Result compile(final Path previousClasses, final Source... sources) {
    final var classpath =
        previousClasses == null
            ? System.getProperty("java.class.path")
            : previousClasses + File.pathSeparator + System.getProperty("java.class.path");
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
            List.of("-encoding", "UTF-8", "-classpath", classpath),
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

  record Source(String name, String code) {}

  record Result(
      List<String> errors, Map<String, ByteArrayOutputStream> output, ClassLoader loader) {
    Result(final List<String> errors, final Map<String, ByteArrayOutputStream> output) {
      this(errors, output, loader(output));
    }

    /**
     * Writes the compiled classes as class files, to be used as the path of another compilation.
     */
    void writeTo(final Path directory) throws IOException {
      for (final var entry : output.entrySet()) {
        final var file = directory.resolve(entry.getKey().replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, entry.getValue().toByteArray());
      }
    }

    Class<?> load(final String className) throws ClassNotFoundException {
      return Class.forName(className, true, loader);
    }

    private static ClassLoader loader(final Map<String, ByteArrayOutputStream> output) {
      return new ClassLoader(InMemoryCompiler.class.getClassLoader()) {
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
