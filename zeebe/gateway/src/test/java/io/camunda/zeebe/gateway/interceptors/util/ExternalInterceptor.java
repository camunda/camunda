/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.util;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.io.File;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;

public final class ExternalInterceptor {
  public static final String CLASS_NAME = "com.acme.ExternalInterceptor";

  /**
   * Creates a new, unloaded class - that is, unavailable via any existing class loaders - which
   * implements {@link ServerInterceptor}. The implementation of {@link
   * ServerInterceptor#interceptCall(ServerCall, Metadata, ServerCallHandler)}} here simply
   * delegates to {@link ExternalInterceptorImpl}. The class also defines a {@link String} constant
   * called {@code FOO} which returns the value {@code "bar"}.
   *
   * <p>The class is created with {@link #CLASS_NAME} as its canonical class name.
   *
   * <p>You can easily create a JAR from this class by using {@link Unloaded#toJar(File)}.
   *
   * @return the unloaded class
   */
  public static Unloaded<ServerInterceptor> createUnloadedInterceptorClass() {
    return new ByteBuddy()
        .subclass(ServerInterceptor.class)
        .name(CLASS_NAME)
        .method(ElementMatchers.named("interceptCall"))
        .intercept(MethodDelegation.to(ExternalInterceptorImpl.class))
        .defineField("FOO", String.class, Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC)
        .value("bar")
        .make();
  }

  /**
   * Must be public so that the ByteBuddy generated class can find it to ensure we have some default
   * implementation.
   */
  public static final class ExternalInterceptorImpl {
    public static <ReqT, RespT> Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next) {
      headers.put(Key.of("FOO", Metadata.ASCII_STRING_MARSHALLER), "BAR");
      return next.startCall(call, headers);
    }
  }
}
