/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.impl;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.PrimitiveTypeRegistry;
import io.atomix.utils.ServiceException;
import io.atomix.utils.misc.StringUtils;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Classpath scanning primitive type registry. */
public class ClasspathScanningPrimitiveTypeRegistry implements PrimitiveTypeRegistry {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClasspathScanningPrimitiveTypeRegistry.class);

  private static final Map<ClassLoader, Map<String, PrimitiveType>> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private final Map<String, PrimitiveType> primitiveTypes = new ConcurrentHashMap<>();

  public ClasspathScanningPrimitiveTypeRegistry(final ClassLoader classLoader) {
    final Map<String, PrimitiveType> types =
        CACHE.computeIfAbsent(
            classLoader,
            cl -> {
              final Map<String, PrimitiveType> result = new ConcurrentHashMap<>();
              final String[] whitelistPackages =
                  StringUtils.split(System.getProperty("io.atomix.whitelistPackages"), ",");
              final ClassGraph classGraph =
                  whitelistPackages != null
                      ? new ClassGraph()
                          .enableClassInfo()
                          .whitelistPackages(whitelistPackages)
                          .addClassLoader(classLoader)
                      : new ClassGraph().enableClassInfo().addClassLoader(classLoader);
              try (final ScanResult scanResult = classGraph.scan()) {
                scanResult
                    .getClassesImplementing(PrimitiveType.class.getName())
                    .forEach(
                        classInfo -> {
                          if (classInfo.isInterface()
                              || classInfo.isAbstract()
                              || Modifier.isPrivate(classInfo.getModifiers())) {
                            return;
                          }
                          final PrimitiveType primitiveType = newInstance(classInfo.loadClass());
                          final PrimitiveType oldPrimitiveType =
                              result.put(primitiveType.name(), primitiveType);
                          if (oldPrimitiveType != null) {
                            LOGGER.warn(
                                "Found multiple primitives types name={}, classes=[{}, {}]",
                                primitiveType.name(),
                                oldPrimitiveType.getClass().getName(),
                                primitiveType.getClass().getName());
                          }
                        });
              }
              return Collections.unmodifiableMap(result);
            });
    primitiveTypes.putAll(types);
  }

  /**
   * Instantiates the given type using a no-argument constructor.
   *
   * @param type the type to instantiate
   * @param <T> the generic type
   * @return the instantiated object
   * @throws ServiceException if the type cannot be instantiated
   */
  @SuppressWarnings("unchecked")
  private static <T> T newInstance(final Class<?> type) {
    try {
      return (T) type.newInstance();
    } catch (final InstantiationException | IllegalAccessException e) {
      throw new ServiceException("Cannot instantiate service class " + type, e);
    }
  }

  @Override
  public Collection<PrimitiveType> getPrimitiveTypes() {
    return primitiveTypes.values();
  }

  @Override
  public PrimitiveType getPrimitiveType(final String typeName) {
    final PrimitiveType type = primitiveTypes.get(typeName);
    if (type == null) {
      throw new ServiceException("Unknown primitive type " + typeName);
    }
    return type;
  }
}
