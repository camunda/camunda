/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StreamUtils;

public class ExternalResourcesUtil extends HttpServlet {

  private static final String INDEX_FILE = "/index.html";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();

  static {
    MIME_TYPE_MAP.put("pdf", "application/pdf");
    MIME_TYPE_MAP.put("png", "image/png");
    MIME_TYPE_MAP.put("jpg", "image/jpeg");
    MIME_TYPE_MAP.put("jpeg", "image/jpeg");
    MIME_TYPE_MAP.put("gif", "image/gif");
    MIME_TYPE_MAP.put("html", "text/html");
    MIME_TYPE_MAP.put("css", "text/css");
    MIME_TYPE_MAP.put("js", "application/javascript");
    MIME_TYPE_MAP.put("json", "application/json");
    MIME_TYPE_MAP.put("xml", "application/xml");
    MIME_TYPE_MAP.put("txt", "text/plain");
  }

  public static void serveStaticResource(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final ServletContext servletContext,
      final String clusterId)
      throws ServletException, IOException {
    String filename = request.getRequestURI().replaceFirst("/external", ""); // e.g., /someFile.txt
    filename = stripContextPath(filename, request);
    filename = stripClusterIdPath(filename, clusterId);

    if ("/".equals(filename) || filename.isEmpty()) {
      filename = INDEX_FILE;
    }

    /* serve file */
    try {
      final InputStream fileStream = servletContext.getResourceAsStream(filename);
      final String mimeType = getMimeType(filename);
      response.setContentType(mimeType != null ? mimeType : DEFAULT_MIME_TYPE);
      StreamUtils.copy(fileStream, response.getOutputStream());
      response.flushBuffer();
    } catch (Exception exception) {
      throw new IOException("Cannot stream external resource: " + filename, exception);
    }
  }

  public static boolean shouldServeStaticResource(
      final HttpServletRequest request, final String clusterId) {
    String requestURI = request.getRequestURI();
    requestURI = stripContextPath(requestURI, request);
    requestURI = stripClusterIdPath(requestURI, clusterId);
    final boolean result =
        requestURI != null
            && requestURI.startsWith("/external")
            && !requestURI.startsWith("/external/api")
            && "GET".equals(request.getMethod());

    return result;
  }

  public static String getMimeType(final String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return DEFAULT_MIME_TYPE;
    }

    final String extension = getFileExtension(fileName);
    return MIME_TYPE_MAP.getOrDefault(extension, DEFAULT_MIME_TYPE);
  }

  private static String getFileExtension(final String fileName) {
    final int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
      return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
    return "";
  }

  public static String stripContextPath(final String filename, final HttpServletRequest request) {
    final String contextPath = request.getContextPath();
    String newName = filename;
    if (!contextPath.isEmpty() && filename.startsWith(contextPath)) {
      newName = filename.substring(contextPath.length());
    }
    return newName;
  }

  private static String stripClusterIdPath(final String filename, final String clusterId) {
    if (clusterId == null || clusterId.isEmpty()) {
      return filename;
    }

    final String clusterPath = "/" + clusterId;
    String newName = filename;
    if (filename.startsWith(clusterPath)) {
      newName = filename.substring(clusterPath.length());
    }
    return newName;
  }

  public static void serveStaticResource(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final ServletContext servletContext)
      throws ServletException, IOException {
    serveStaticResource(request, response, servletContext, null);
  }

  public static boolean shouldServeStaticResource(final HttpServletRequest request) {
    return shouldServeStaticResource(request, null);
  }
}
