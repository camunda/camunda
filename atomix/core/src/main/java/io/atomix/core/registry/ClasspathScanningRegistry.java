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
package io.atomix.core.registry;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Sets;
import io.atomix.core.AtomixRegistry;
import io.atomix.utils.ConfiguredType;
import io.atomix.utils.NamedType;
import io.atomix.utils.ServiceException;
import io.atomix.utils.misc.StringUtils;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Atomix registry that scans the classpath for registered objects. */
public final class ClasspathScanningRegistry implements AtomixRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathScanningRegistry.class);
  private static final Map<ClassLoader, Map<Class<? extends NamedType>, Map<String, NamedType>>>
      CACHE = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<Class<? extends NamedType>, Map<String, NamedType>> registrations =
      new ConcurrentHashMap<>();

  private ClasspathScanningRegistry(final ClassLoader classLoader) {
    this(classLoader, Sets.newHashSet());
  }

  @SuppressWarnings("unchecked")
  private ClasspathScanningRegistry(
      final ClassLoader classLoader, final Set<String> whitelistPackages) {
    final Map<Class<? extends NamedType>, Map<String, NamedType>> registrations =
        CACHE.computeIfAbsent(
            classLoader,
            cl -> {
              final ClassGraph classGraph =
                  !whitelistPackages.isEmpty()
                      ? new ClassGraph()
                          .enableClassInfo()
                          .whitelistPackages(whitelistPackages.toArray(new String[0]))
                          .addClassLoader(classLoader)
                      : new ClassGraph().enableClassInfo().addClassLoader(classLoader);
              try (final ScanResult scanResult = classGraph.scan()) {
                final Map<Class<? extends NamedType>, Map<String, NamedType>> result =
                    new ConcurrentHashMap<>();
                scanResult
                    .getClassesImplementing(NamedType.class.getName())
                    .forEach(
                        classInfo -> {
                          if (classInfo.isInterface()
                              || classInfo.isAbstract()
                              || Modifier.isPrivate(classInfo.getModifiers())) {
                            return;
                          }
                          final Class<?> type = classInfo.loadClass();
                          final Class<? extends NamedType> classType = getClassType(type);
                          final NamedType instance = newInstance(type);
                          final NamedType oldInstance =
                              result
                                  .computeIfAbsent(classType, t -> new HashMap<>())
                                  .put(instance.name(), instance);
                          if (oldInstance != null) {
                            LOGGER.warn(
                                "Found multiple types with name={}, classes=[{}, {}]",
                                instance.name(),
                                oldInstance.getClass().getName(),
                                instance.getClass().getName());
                          }
                        });
                return result;
              }
            });
    this.registrations.putAll(registrations);
  }

  /**
   * Returns a new classpath scanning registry builder.
   *
   * @return a new classpath scanning registry builder
   */
  public static Builder builder() {
    return new Builder();
  }

  private Class<? extends NamedType> getClassType(Class<?> type) {
    while (type != Object.class) {
      final Class<? extends NamedType> baseType = getInterfaceType(type);
      if (baseType != null) {
        return baseType;
      }
      type = type.getSuperclass();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Class<? extends NamedType> getInterfaceType(Class<?> type) {
    for (final Class<?> iface : type.getInterfaces()) {
      if (iface == ConfiguredType.class || iface == NamedType.class) {
        return (Class<? extends NamedType>) type;
      }
    }
    for (final Class<?> iface : type.getInterfaces()) {
      type = getInterfaceType(iface);
      if (type != null) {
        return (Class<? extends NamedType>) type;
      }
    }
    return null;
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
  @SuppressWarnings("unchecked")
  public <T extends NamedType> Collection<T> getTypes(final Class<T> type) {
    final Map<String, NamedType> types = registrations.get(type);
    return types != null ? (Collection<T>) types.values() : Collections.emptyList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends NamedType> T getType(final Class<T> type, final String name) {
    final Map<String, NamedType> types = registrations.get(type);
    return types != null ? (T) types.get(name) : null;
  }

  /** Classpath scanning registry builder. */
  public static final class Builder implements io.atomix.utils.Builder<AtomixRegistry> {
    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    private Set<String> whitelistPackages = Sets.newHashSet();

    private Builder() {
      final String whitelistPackages = System.getProperty("io.atomix.whitelistPackages");
      if (whitelistPackages != null) {
        this.whitelistPackages = Sets.newHashSet(StringUtils.split(whitelistPackages, ","));
      }
    }

    /**
     * Sets the classpath scanner class loader.
     *
     * @param classLoader the classpath scanner class loader
     * @return the registry builder
     */
    public Builder withClassLoader(final ClassLoader classLoader) {
      this.classLoader = checkNotNull(classLoader, "classLoader cannot be null");
      return this;
    }

    /**
     * Sets the whitelist packages.
     *
     * <p>When whitelist packages are provided, the classpath scanner will only scan those packages
     * which are specified.
     *
     * @param whitelistPackages the whitelist packages
     * @return the registry builder
     */
    public Builder withWhitelistPackages(final String... whitelistPackages) {
      return withWhitelistPackages(Sets.newHashSet(whitelistPackages));
    }

    /**
     * Sets the whitelist packages.
     *
     * <p>When whitelist packages are provided, the classpath scanner will only scan those packages
     * which are specified.
     *
     * @param whitelistPackages the whitelist packages
     * @return the registry builder
     */
    public Builder withWhitelistPackages(final Collection<String> whitelistPackages) {
      this.whitelistPackages = Sets.newHashSet(whitelistPackages);
      return this;
    }

    /**
     * Adds a package to the whitelist.
     *
     * @param whitelistPackage the package to add
     * @return the registry builder
     */
    public Builder addWhitelistPackage(final String whitelistPackage) {
      checkNotNull(whitelistPackage, "whitelistPackage cannot be null");
      whitelistPackages.add(whitelistPackage);
      return this;
    }

    @Override
    public AtomixRegistry build() {
      return new ClasspathScanningRegistry(classLoader, whitelistPackages);
    }
  }
}
