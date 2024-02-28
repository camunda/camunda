/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.j5templates;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class TestResourceManager {

  public String readResourceFileContentsAsString(String resource) {
    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(resource)) {
      if (resourceStream != null) {
        return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
      } else {
        throw new FileNotFoundException(resource);
      }
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot read resource from classpath. %s", e.getMessage());
      throw new RuntimeException(exceptionMsg, e);
    }
  }
}
