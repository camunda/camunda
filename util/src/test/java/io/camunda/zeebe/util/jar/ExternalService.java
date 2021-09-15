/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.jar;

import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.jar.asm.Opcodes;

final class ExternalService {
  static final String CLASS_NAME = "com.acme.ExternalService";

  /**
   * Creates a new, unloaded class - that is, unavailable via any existing class loaders - which
   * implements the given service interface. The class defines a {@link String} constant called
   * {@code FOO} which returns the value {@code "bar"}.
   *
   * <p>The class is created with {@link #CLASS_NAME} as its canonical class name.
   *
   * <p>You can easily create a JAR from this class by using {@link Unloaded#toJar(File)}.
   *
   * @return the unloaded class
   */
  static Unloaded<Service> createUnloadedExporterClass() {
    return new ByteBuddy()
        .subclass(Service.class)
        .name(CLASS_NAME)
        .defineField("FOO", String.class, Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC)
        .value("bar")
        .make();
  }

  public interface Service {}
}
