/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.interceptors.util.ExternalInterceptor;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class InterceptorRepositoryTest {
  private final InterceptorRepository repository = new InterceptorRepository();

  @Test
  void shouldFailToLoadNonInterceptorClassFromClassPath() {
    // given
    final var id = "myInterceptor";
    final var config = new InterceptorCfg();
    config.setClassName(String.class.getName());
    config.setId(id);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(InterceptorLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldFailToLoadNonExistingClassFromClassPath() {
    // given
    final var id = "myInterceptor";
    final var config = new InterceptorCfg();
    config.setClassName("a");
    config.setId(id);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(InterceptorLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldLoadInterceptorFromClassPath()
      throws InterceptorLoadException, ExternalJarLoadException {
    // given
    final var id = "myInterceptor";
    final var config = new InterceptorCfg();
    config.setClassName(MinimalInterceptor.class.getName());
    config.setJarPath(null);
    config.setId(id);

    // when
    final var loadedClass = repository.load(config);

    // then
    assertThat(config.isExternal()).isFalse();
    assertThat(loadedClass).isEqualTo(MinimalInterceptor.class);
    assertThat(loadedClass.getClassLoader()).isEqualTo(getClass().getClassLoader());
    assertThat(repository.getInterceptors()).containsEntry(id, loadedClass);
  }

  @Test
  void shouldLoadInterceptorFromJar(final @TempDir File tempDir) throws Exception {
    // given
    final var id = "myInterceptor";
    final var interceptorClass = ExternalInterceptor.createUnloadedInterceptorClass();
    final var jarFile = interceptorClass.toJar(new File(tempDir, "interceptor.jar"));
    final var config = new InterceptorCfg();

    // when
    config.setClassName(ExternalInterceptor.CLASS_NAME);
    config.setJarPath(jarFile.getAbsolutePath());
    config.setId(id);

    // when
    final var loadedClass = repository.load(config);

    // then
    assertThat(config.isExternal()).isTrue();
    assertThat(ServerInterceptor.class.isAssignableFrom(loadedClass))
        .as("the loaded class implements ServerInterceptor")
        .isTrue();
    assertThat(loadedClass.getClassLoader()).isNotEqualTo(getClass().getClassLoader());
    assertThat(repository.getInterceptors()).containsEntry(id, loadedClass);
  }

  @Test
  void shouldFailToLoadNonInterceptorClassFromJar(final @TempDir File tempDir) throws IOException {
    // given
    final var id = "myInterceptor";
    final var externalClass =
        new ByteBuddy().subclass(Object.class).name("com.acme.MyObject").make();
    final var jarFile = externalClass.toJar(new File(tempDir, "library.jar"));
    final var config = new InterceptorCfg();

    // when
    config.setId(id);
    config.setClassName("com.acme.MyObject");
    config.setJarPath(jarFile.getAbsolutePath());

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(InterceptorLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldFailToLoadNonExistingClassFromJar(final @TempDir File tempDir) throws IOException {
    // given
    final var id = "myInterceptor";
    final var interceptorClass = ExternalInterceptor.createUnloadedInterceptorClass();
    final var jarFile = interceptorClass.toJar(new File(tempDir, "interceptor.jar"));
    final var config = new InterceptorCfg();

    // when
    config.setId(id);
    config.setClassName("xyz.i.dont.Exist");
    config.setJarPath(jarFile.getAbsolutePath());

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(InterceptorLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldLoadExternalInterceptorRelativeToBasedir(final @TempDir File tempDir)
      throws IOException {
    // given
    final var baseRepository =
        new InterceptorRepository(new HashMap<>(), new ExternalJarRepository(), tempDir.toPath());
    final var id = "myInterceptor";
    final var interceptorClass = ExternalInterceptor.createUnloadedInterceptorClass();
    final var jarFile = interceptorClass.toJar(new File(tempDir, "interceptor.jar"));
    final var config = new InterceptorCfg();

    // when
    config.setId(id);
    config.setClassName(ExternalInterceptor.CLASS_NAME);
    config.setJarPath(jarFile.getName());

    // when
    final Class<? extends ServerInterceptor> loadedClass;
    loadedClass = baseRepository.load(config);

    // then
    assertThat(ServerInterceptor.class.isAssignableFrom(loadedClass))
        .as("the loaded class implements ServerInterceptor")
        .isTrue();
  }

  @Test
  void shouldInstantiateInterceptors(final @TempDir File tempDir) throws IOException {
    // given
    final var internalConfig = new InterceptorCfg();
    internalConfig.setClassName(MinimalInterceptor.class.getName());
    internalConfig.setJarPath(null);
    internalConfig.setId("internal");
    final var externalClass = ExternalInterceptor.createUnloadedInterceptorClass();
    final var jarFile = externalClass.toJar(new File(tempDir, "interceptor.jar"));
    final var externalConfig = new InterceptorCfg();
    externalConfig.setClassName(ExternalInterceptor.CLASS_NAME);
    externalConfig.setJarPath(jarFile.getAbsolutePath());
    externalConfig.setId("external");

    // when
    final var interceptors = repository.load(List.of(internalConfig, externalConfig)).instantiate();

    // then
    final var loadedClass = repository.getInterceptors().get("external");
    assertThat(interceptors)
        .hasSize(2)
        .map(interceptor -> (Class) interceptor.getClass())
        .containsExactlyInAnyOrder(MinimalInterceptor.class, loadedClass);
  }

  public static final class MinimalInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
        final ServerCall<ReqT, RespT> call,
        final Metadata headers,
        final ServerCallHandler<ReqT, RespT> next) {
      return next.startCall(call, headers);
    }
  }
}
