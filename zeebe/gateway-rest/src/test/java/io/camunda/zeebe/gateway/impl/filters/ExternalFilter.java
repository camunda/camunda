/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.File;
import java.io.IOException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;

public final class ExternalFilter {
  public static final String CLASS_NAME = "com.acme.ExternalFilter";

  /**
   * Creates a new, unloaded class - that is, unavailable via any existing class loaders - which
   * implements {@link jakarta.servlet.Filter}. The implementation of {@link
   * Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}} here simply delegates to {@link
   * ExternalFilterImpl}. The class also defines a {@link String} attribute called {@code FOO} which
   * returns the value {@code "bar"}.
   *
   * <p>The class is created with {@link #CLASS_NAME} as its canonical class name.
   *
   * <p>You can easily create a JAR from this class by using {@link Unloaded#toJar(File)}.
   *
   * @return the unloaded class
   */
  public static Unloaded<Filter> createUnloadedFilterClass() {
    return new ByteBuddy()
        .subclass(Filter.class)
        .name(CLASS_NAME)
        .method(ElementMatchers.named("doFilter"))
        .intercept(MethodDelegation.to(ExternalFilterImpl.class))
        .defineField("FOO", String.class, Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC)
        .value("bar")
        .make();
  }

  /**
   * Must be public so that the ByteBuddy generated class can find it to ensure we have some default
   * implementation.
   */
  public static final class ExternalFilterImpl {
    public static void doFilter(
        final ServletRequest servletRequest,
        final ServletResponse servletResponse,
        final FilterChain filterChain)
        throws ServletException, IOException {
      servletRequest.setAttribute("FOO", "bar");
      filterChain.doFilter(servletRequest, servletResponse);
    }
  }
}
