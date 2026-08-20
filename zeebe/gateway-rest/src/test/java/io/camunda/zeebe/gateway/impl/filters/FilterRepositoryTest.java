/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterLoadException;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterRepository;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class FilterRepositoryTest {
  private final FilterRepository repository = new FilterRepository();

  @Test
  void shouldFailToLoadNonFilterClassFromClassPath() {
    // given
    final var id = "myFilter";
    final var config = new FilterCfg();
    config.setClassName(String.class.getName());
    config.setId(id);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(FilterLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldFailToLoadNonExistingClassFromClassPath() {
    // given
    final var id = "myFilter";
    final var config = new FilterCfg();
    config.setClassName("a");
    config.setId(id);

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(FilterLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldLoadFilterFromClassPath() throws FilterLoadException, ExternalJarLoadException {
    // given
    final var id = "myFilter";
    final var config = new FilterCfg();
    config.setClassName(MinimalFilter.class.getName());
    config.setJarPath(null);
    config.setId(id);

    // when
    final var loadedClass = repository.load(config);

    // then
    assertThat(config.isExternal()).isFalse();
    assertThat(loadedClass).isEqualTo(MinimalFilter.class);
    assertThat(loadedClass.getClassLoader()).isEqualTo(getClass().getClassLoader());
    assertThat(repository.getFilters()).containsEntry(id, loadedClass);
  }

  @Test
  void shouldLoadFilterFromJar(final @TempDir File tempDir) throws Exception {
    // given
    final var id = "myFilter";
    final var filterClass = ExternalFilter.createUnloadedFilterClass();
    final var jarFile = filterClass.toJar(new File(tempDir, "filter.jar"));
    final var config = new FilterCfg();

    // when
    config.setClassName(ExternalFilter.CLASS_NAME);
    config.setJarPath(jarFile.getAbsolutePath());
    config.setId(id);

    // when
    final var loadedClass = repository.load(config);

    // then
    assertThat(config.isExternal()).isTrue();
    assertThat(Filter.class.isAssignableFrom(loadedClass))
        .as("the loaded class implements Filter")
        .isTrue();
    assertThat(loadedClass.getClassLoader()).isNotEqualTo(getClass().getClassLoader());
    assertThat(repository.getFilters()).containsEntry(id, loadedClass);
  }

  @Test
  void shouldFailToLoadNonFilterClassFromJar(final @TempDir File tempDir) throws IOException {
    // given
    final var id = "myFilter";
    final var externalClass =
        new ByteBuddy().subclass(Object.class).name("com.acme.MyObject").make();
    final var jarFile = externalClass.toJar(new File(tempDir, "library.jar"));
    final var config = new FilterCfg();

    // when
    config.setId(id);
    config.setClassName("com.acme.MyObject");
    config.setJarPath(jarFile.getAbsolutePath());

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(FilterLoadException.class)
        .hasCauseInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldFailToLoadNonExistingClassFromJar(final @TempDir File tempDir) throws IOException {
    // given
    final var id = "myFilter";
    final var filterClass = ExternalFilter.createUnloadedFilterClass();
    final var jarFile = filterClass.toJar(new File(tempDir, "filter.jar"));
    final var config = new FilterCfg();

    // when
    config.setId(id);
    config.setClassName("xyz.i.dont.Exist");
    config.setJarPath(jarFile.getAbsolutePath());

    // then
    assertThatThrownBy(() -> repository.load(config))
        .isInstanceOf(FilterLoadException.class)
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void shouldLoadExternalFilterRelativeToBasedir(final @TempDir File tempDir) throws IOException {
    // given
    final var baseRepository =
        new FilterRepository(new LinkedHashMap<>(), new ExternalJarRepository(), tempDir.toPath());
    final var id = "myFilter";
    final var filterClass = ExternalFilter.createUnloadedFilterClass();
    final var jarFile = filterClass.toJar(new File(tempDir, "filter.jar"));
    final var config = new FilterCfg();

    // when
    config.setId(id);
    config.setClassName(ExternalFilter.CLASS_NAME);
    config.setJarPath(jarFile.getName());

    // when
    final Class<? extends Filter> loadedClass;
    loadedClass = baseRepository.load(config);

    // then
    assertThat(Filter.class.isAssignableFrom(loadedClass))
        .as("the loaded class implements Filter")
        .isTrue();
  }

  @Test
  void shouldInstantiateFilters(final @TempDir File tempDir)
      throws IOException, ClassNotFoundException {
    // given
    final var internalConfig = new FilterCfg();
    internalConfig.setClassName(MinimalFilter.class.getName());
    internalConfig.setJarPath(null);
    internalConfig.setId("internal");
    final var externalClass = ExternalFilter.createUnloadedFilterClass();
    final var jarFile = externalClass.toJar(new File(tempDir, "filter.jar"));
    final var externalConfig = new FilterCfg();
    externalConfig.setClassName(ExternalFilter.CLASS_NAME);
    externalConfig.setJarPath(jarFile.getAbsolutePath());
    externalConfig.setId("external");

    // when
    final var filters = repository.load(List.of(internalConfig, externalConfig)).instantiate();

    // then
    final var loadedClass = repository.getFilters().get("external");
    assertThat(filters)
        .hasSize(2)
        .map(Filter::getClass)
        .containsExactly(MinimalFilter.class, loadedClass);
  }

  public static final class MinimalFilter implements Filter {
    @Override
    public void doFilter(
        final ServletRequest servletRequest,
        final ServletResponse servletResponse,
        final FilterChain filterChain)
        throws IOException, ServletException {
      filterChain.doFilter(servletRequest, servletResponse);
    }
  }
}
