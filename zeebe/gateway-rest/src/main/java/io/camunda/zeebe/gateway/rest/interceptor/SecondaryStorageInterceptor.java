/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
/**
 * Interceptor that validates secondary storage availability for endpoints requiring it.
 * When secondary storage is not configured (database.type=none), requests to endpoints
 * marked with {@link RequiresSecondaryStorage} will be rejected with HTTP 403 Forbidden.
@Component
public class SecondaryStorageInterceptor implements HandlerInterceptor {
  private final DatabaseProperties databaseProperties;
  @Autowired
  public SecondaryStorageInterceptor(final DatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
  }
  @Override
  public boolean preHandle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object handler)
      throws Exception {
    if (handler instanceof HandlerMethod handlerMethod) {
      final boolean requiresSecondaryStorage = 
          handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class) ||
          handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);
      if (requiresSecondaryStorage && !isSecondaryStorageEnabled()) {
        writeSecondaryStorageUnavailableResponse(response);
        return false;
      }
    }
    return true;
  private boolean isSecondaryStorageEnabled() {
    final DatabaseType databaseType = DatabaseType.from(databaseProperties.getType());
    return !databaseType.isNone();
  private void writeSecondaryStorageUnavailableResponse(final HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/problem+json");
    response.getWriter().write(
        """
        {
          "type": "about:blank",
          "title": "Secondary Storage Required",
          "status": 403,
          "detail": "This endpoint requires secondary storage to be configured. The current deployment is running in headless mode (database.type=none). Please configure a secondary storage system to access this functionality."
        }
        """);
  @ConfigurationProperties("camunda.database")
  @Component
  public static class DatabaseProperties {
    private String type = DatabaseConfig.ELASTICSEARCH;
    public String getType() {
      return type;
    public void setType(final String type) {
      this.type = type;
}
