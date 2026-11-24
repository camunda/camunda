/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

/**
 * Generates for every {@link MsgpackPojo} a stateless {@code <Pojo>Codec} and a {@code <Pojo>Ops}.
 */
@SupportedAnnotationTypes("io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo")
public final class MsgpackPojoProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    final var builder = new ModelBuilder(processingEnv);
    for (final Element element : roundEnv.getElementsAnnotatedWith(MsgpackPojo.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        error(element, "@MsgpackPojo can only be applied to classes");
        continue;
      }
      try {
        final var model = builder.build((TypeElement) element);
        write(model.pkg(), model.name() + "Codec", CodecRenderer.codec(model), element);
        write(model.pkg(), model.name() + "Ops", OpsRenderer.ops(model), element);
      } catch (final IllegalArgumentException e) {
        error(element, String.valueOf(e.getMessage()));
      }
    }
    return true;
  }

  private void write(
      final String pkg, final String cls, final String source, final Element origin) {
    try (final Writer writer =
        processingEnv.getFiler().createSourceFile(pkg + "." + cls, origin).openWriter()) {
      writer.write(source);
    } catch (final IOException e) {
      error(origin, "Could not write " + cls + ": " + e.getMessage());
    }
  }

  private void error(final Element element, final String message) {
    processingEnv.getMessager().printMessage(Kind.ERROR, message, element);
  }
}
