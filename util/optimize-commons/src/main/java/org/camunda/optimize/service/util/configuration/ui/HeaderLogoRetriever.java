/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.ui;

import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.tika.Tika;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathAsAbsoluteUrl;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathToStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HeaderLogoRetriever {

  /**
   * We only support logo images that have support for the html img tag.
   * According to developer mozilla, this are the valid mime types for html image tag:
   * https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Image_types
   */
  private static final Set<String> VALID_HTML_IMG_TAG_MIME_TYPES = ImmutableSet.of(
    "image/apng",
    "image/bmp",
    "image/gif",
    "image/x-icon",
    "image/jpeg",
    "image/png",
    "image/svg+xml",
    "image/webp"
  );

  public static String readLogoAsBase64(final String pathToLogoIcon) {
    try (InputStream logo = resolvePathToStream(pathToLogoIcon)) {
      String mimeType = getMimeType(pathToLogoIcon);
      validateMimeTypeIsValidHTMLTagImage(mimeType);
      byte[] fileContent = StreamUtils.copyToByteArray(logo);
      String encodedString = Base64.getEncoder().encodeToString(fileContent);
      return String.format("data:%s;base64,%s", mimeType, encodedString);
    } catch (Exception e) {
      String message = String.format(
        "Could not read logo icon from the given path [%s]! Please make sure you have set a valid path and that the " +
          "icon does exist for the given path.",
        pathToLogoIcon
      );
      throw new OptimizeConfigurationException(message, e);
    }
  }

  private static void validateMimeTypeIsValidHTMLTagImage(String mimeType) {
    if (!VALID_HTML_IMG_TAG_MIME_TYPES.contains(mimeType)) {
      String message = String.format(
        "Unknown mime type for given logo. Found [%s], but supported types are %s.",
        mimeType, VALID_HTML_IMG_TAG_MIME_TYPES
      );
      throw new OptimizeConfigurationException(message);
    }
  }

  private static String getMimeType(final String pathToLogoIcon) throws IOException {
    // we need to use this library to make the check
    // for the mime type independent from the OS.
    Tika tika = new Tika();
    URL pathAsUrl = resolvePathAsAbsoluteUrl(pathToLogoIcon);
    return tika.detect(pathAsUrl);
  }
}
