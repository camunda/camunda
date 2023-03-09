/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MarkdownUtil {

  public String getMarkdownForTextReport(final String markdownText) {
    return "{\n" +
      "         \"configuration\":{\n" +
      "            \"text\":{\n" +
      "               \"root\":{\n" +
      "                  \"children\":[\n" +
      "                     {\n" +
      "                        \"children\":[\n" +
      "                           {\n" +
      "                              \"detail\":0,\n" +
      "                              \"format\":0,\n" +
      "                              \"mode\":\"normal\",\n" +
      "                              \"style\":\"\",\n" +
      "                              \"text\":\"" + markdownText + "\",\n" +
      "                              \"type\":\"text\",\n" +
      "                              \"version\":1\n" +
      "                           }\n" +
      "                        ],\n" +
      "                        \"direction\":\"ltr\",\n" +
      "                        \"format\":\"\",\n" +
      "                        \"indent\":0,\n" +
      "                        \"type\":\"paragraph\",\n" +
      "                        \"version\":1\n" +
      "                     }\n" +
      "                  ],\n" +
      "                  \"direction\":\"ltr\",\n" +
      "                  \"format\":\"\",\n" +
      "                  \"indent\":0,\n" +
      "                  \"type\":\"root\",\n" +
      "                  \"version\":1\n" +
      "               }\n" +
      "            }\n" +
      "         }" +
      "     }";
  }

}
