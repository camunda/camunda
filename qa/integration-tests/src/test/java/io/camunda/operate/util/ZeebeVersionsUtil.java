/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ZeebeVersionsUtil {

  private static final String VERSIONS_FILE = "/zeebe-versions.properties";
  public static final String ZEEBE_VERSIONS_PROPERTY_NAME = "zeebe.versions";
  public static final String ZEEBE_CURRENTVERSION_PROPERTY_NAME = "zeebe.currentVersion";
  public static final String VERSIONS_DELIMITER = ",";

  public static String readProperty(String propertyName) {
    try (InputStream propsFile = ZeebeVersionsUtil.class.getResourceAsStream(VERSIONS_FILE)) {
      Properties props = new Properties();
      props.load(propsFile);
      return props.getProperty(propertyName);
    } catch (IOException e) {
      throw new OperateRuntimeException("Unable to read the list of supported Zeebe zeebeVersions.", e);
    }
  }

}
