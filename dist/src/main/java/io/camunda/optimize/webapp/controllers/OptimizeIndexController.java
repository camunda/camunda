/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.webapp.controllers;

import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class OptimizeIndexController {

  @Autowired private ServletContext context;
  @Autowired private WebappsRequestForwardManager webappsRequestForwardManager;
  @Autowired private ResourcePatternResolver resourcePatternResolver;

  @GetMapping("/optimize")
  public String optimize(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/optimize/");
    return "optimize/index.html";
  }

  @RequestMapping(value = {"/optimize/{regex:[\\w-]+}", "/optimize/**/{regex:[\\w-]+}"})
  public String forwardToOptimize(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "optimize");
  }

  private void printAvailableResources() throws IOException {
    final String[] unwantedExtensions = {".class", ".txt", ".lombok"};
    final Resource[] resources = resourcePatternResolver.getResources("classpath*:**/*");
    System.out.println("#### Available Resources ####");
    int count = 0;
    for (final Resource resource : resources) {
      final String name = resource.getFilename();
      if (Arrays.stream(unwantedExtensions).anyMatch(e -> name.endsWith(e))) {
        continue;
      }

      if ("index.html".equals(name)) {
        final String content = resource.getContentAsString(Charset.defaultCharset());
        System.out.println("");
        System.out.println("Occurrence #" + count++);
        System.out.println(resource.getURI());
        System.out.println("");
        System.out.println(content);
      }
    }
    System.out.println("#############################");
  }
}
