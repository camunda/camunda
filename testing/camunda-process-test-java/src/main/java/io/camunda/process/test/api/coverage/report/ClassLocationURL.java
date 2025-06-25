/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api.coverage.report;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;

/** Based on info found at http://stackoverflow.com/questions/15359702/get-location-of-jar-file */
public class ClassLocationURL {

  static File fileFor(final Class aclass) {
    final URL location = locationFor(aclass);
    if (location == null) {
      throw new IllegalStateException("Could not locate " + aclass.getCanonicalName());
    }
    try {
      return new File(location.toURI());
    } catch (final URISyntaxException ex) {
      return new File(location.getPath());
    }
  }

  static URL locationFor(final Class aclass) {
    final URL location = urlFromCodeSource(aclass);
    return location != null ? location : urlFromResource(aclass);
  }

  static URL urlFromCodeSource(final Class aclass) {
    try {
      final CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();
      final URL url = codeSource != null ? codeSource.getLocation() : null;
      // url is in one of two forms
      // 1) ./build/classes/
      // 2) jardir/JarName.jar
      return url;
    } catch (final SecurityException ex) {
      return null;
    }
  }

  static URL urlFromResource(final Class aclass) {
    final URL url = aclass.getResource(aclass.getSimpleName() + ".class");
    // 1) file:/U:/java/Tools/UI/build/classes/org/junit/Test.class
    // 2) jar:file:/U:/java/Tools/UI/dist/UI.jar!/org/junit/Test.class
    // We transform those forms to the forms delivered by urlFromCodeSource
    final String suffix = (aclass.getName()).replace(".", "/") + ".class";
    try {
      final String u = url.toExternalForm();
      return new URL(
          u.startsWith("jar:")
              ? u.replaceFirst("jar:", "").replace("!/" + suffix, "")
              : u.replace(suffix, ""));
    } catch (final MalformedURLException e) {
      return null;
    }
  }
}
