/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

public final class MarkdownUtil {

  private MarkdownUtil() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static String getMarkdownForTextReport(final String markdownText) {
    return "{\n"
        + "         \"configuration\":{\n"
        + "            \"text\":{\n"
        + "               \"root\":{\n"
        + "                  \"children\":[\n"
        + "                     {\n"
        + "                        \"children\":[\n"
        + "                           {\n"
        + "                              \"detail\":0,\n"
        + "                              \"format\":0,\n"
        + "                              \"mode\":\"normal\",\n"
        + "                              \"style\":\"\",\n"
        + "                              \"text\":\""
        + markdownText
        + "\",\n"
        + "                              \"type\":\"text\",\n"
        + "                              \"version\":1\n"
        + "                           }\n"
        + "                        ],\n"
        + "                        \"direction\":\"ltr\",\n"
        + "                        \"format\":\"\",\n"
        + "                        \"indent\":0,\n"
        + "                        \"type\":\"paragraph\",\n"
        + "                        \"version\":1\n"
        + "                     }\n"
        + "                  ],\n"
        + "                  \"direction\":\"ltr\",\n"
        + "                  \"format\":\"\",\n"
        + "                  \"indent\":0,\n"
        + "                  \"type\":\"root\",\n"
        + "                  \"version\":1\n"
        + "               }\n"
        + "            }\n"
        + "         }"
        + "     }";
  }
}
