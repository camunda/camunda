/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.lang.reflect.Method;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class CamundaRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  private static final String ENGINE_PREFIX = "/engines/{engineName}";

  @Override
  protected RequestMappingInfo getMappingForMethod(
      final Method method, final Class<?> handlerType) {
    final var mapping = super.getMappingForMethod(method, handlerType);
    if (mapping == null) {
      return null;
    }

    final var controllerAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(handlerType, CamundaRestController.class);
    if (controllerAnnotation == null || controllerAnnotation.path().isEmpty()) {
      return mapping;
    }

    final String basePath = controllerAnnotation.path();
    final String enginePath = ENGINE_PREFIX + basePath;

    // Build dual-path mapping
    final var patternsCondition = mapping.getPathPatternsCondition();
    if (patternsCondition != null) {
      final var patterns = patternsCondition.getPatterns();
      final var dualPatterns =
          patterns.stream()
              .flatMap(
                  pattern -> {
                    final String patternValue = pattern.getPatternString();
                    // Replace base path with both variants
                    final String withEngine = patternValue.replaceFirst("^" + basePath, enginePath);
                    return java.util.stream.Stream.of(patternValue, withEngine);
                  })
              .distinct()
              .toArray(String[]::new);

      return RequestMappingInfo.paths(dualPatterns)
          .methods(
              mapping
                  .getMethodsCondition()
                  .getMethods()
                  .toArray(new org.springframework.web.bind.annotation.RequestMethod[0]))
          .params(mapping.getParamsCondition().getExpressions().toArray(new String[0]))
          .headers(mapping.getHeadersCondition().getExpressions().toArray(new String[0]))
          .consumes(
              mapping.getConsumesCondition().getConsumableMediaTypes().stream()
                  .map(Object::toString)
                  .toArray(String[]::new))
          .produces(
              mapping.getProducesCondition().getProducibleMediaTypes().stream()
                  .map(Object::toString)
                  .toArray(String[]::new))
          .build();
    }

    return mapping;
  }
}
