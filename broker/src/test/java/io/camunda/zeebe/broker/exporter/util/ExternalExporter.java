package io.camunda.zeebe.broker.exporter.util;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.protocol.record.Record;
import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;

public final class ExternalExporter {
  public static final String EXPORTER_CLASS_NAME = "com.acme.ExternalExporter";

  /**
   * Creates a new, unloaded class - that is, unavailable via any existing class loaders - which
   * implements {@link Exporter}. The implementation of {@link Exporter#export(Record)} simply does
   * nothing. The class also defines a {@link String} constant called {@code FOO} which returns the
   * value {@code "bar"}.
   *
   * <p>The class is created with {@link #EXPORTER_CLASS_NAME} as its canonical class name.
   *
   * <p>You can easily create a JAR from this class by using {@link Unloaded#toJar(File)}.
   *
   * @return the unloaded class
   */
  public static Unloaded<Exporter> createUnloadedExporterClass() {
    return new ByteBuddy()
        .subclass(Exporter.class)
        .name(EXPORTER_CLASS_NAME)
        .method(ElementMatchers.named("export"))
        .intercept(StubMethod.INSTANCE)
        .defineField("FOO", String.class, Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC)
        .value("bar")
        .make();
  }
}
