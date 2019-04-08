/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.bpm.platform.servlet.purge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;
import org.camunda.bpm.engine.impl.management.PurgeReport;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Servlet that deployes
 */
@WebServlet(name="DeployBasicProcessServlet", urlPatterns={"/*"})
public class PurgeServlet extends HttpServlet {
  private List<ManagementServiceImpl> managementServices;
  private ObjectMapper objectMapper;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    managementServices = (List<ManagementServiceImpl>) config.getServletContext().getAttribute("managementServices");
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    for (ManagementServiceImpl managementService : managementServices) {
      PurgeReport purgeReport = managementService.purge();
      resp.setCharacterEncoding("UTF-8");
      resp.getWriter().println(objectMapper.writeValueAsString(purgeReport));
    }
    resp.getWriter().flush();
    resp.setStatus(200);
    resp.setContentType("application/json");
  }
}
