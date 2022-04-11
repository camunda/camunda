/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

@Slf4j
public class PluginClassLoader extends URLClassLoader {

  private static final String JAVA_PACKAGE_PREFIX = "java.";

  // these excluded package prefixes assure the interfaces in the Optimize plugin framework are loaded by Optimize
  // and need to be maintained in case new third-party libraries are added to the interfaces
  private static final Set<String> EXCLUDED_PACKAGE_PREFIXES = Sets.newHashSet(
    "org.camunda.optimize.plugin",
    "javax.servlet",
    "javax.ws.rs"
  );


  public PluginClassLoader(URL jarFileUrl, ClassLoader parent) {
    super(new URL[]{jarFileUrl}, parent);
  }


  private boolean isInExcludedChildFirstPackagePrefixes(String className) {
    return EXCLUDED_PACKAGE_PREFIXES.stream()
      .anyMatch(className::startsWith);
  }

  @Override
  public Class<?> loadClass(final String className) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(className)) {
      if (className.startsWith(JAVA_PACKAGE_PREFIX)) {
        return findSystemClass(className);
      }

      // check if class is part of our excluded package set use standard ClassLoader (parent first delegation)
      if (isInExcludedChildFirstPackagePrefixes(className)) {
        log.trace("Class '{}' is from our plugin framework or an dependency. Delegating to parent.", className);
        return super.loadClass(className);
      }

      // check whether it is already loaded
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null) {
        log.trace("Found already loaded class '{}'", className);
        return loadedClass;
      }

      try {
        loadedClass = findClass(className);
        log.trace("Found class '{}' in plugin classpath", className);
        return loadedClass;
      } catch (ClassNotFoundException e) {
        // ignore and try next
      }

      log.trace("Couldn't find class '{}' in plugin classpath. Delegating to parent", className);

      // use the standard ClassLoader (which follows normal parent delegation)
      return super.loadClass(className);
    }
  }

  /**
   * returns a plugin resource without taking parent classloaders or system classloaders into account
   */
  public InputStream getPluginResourceAsStream(String name) throws IOException {
    final URL resource = findResource(name);

    if (resource != null) {
      return resource.openStream();
    }
    return null;
  }

}

