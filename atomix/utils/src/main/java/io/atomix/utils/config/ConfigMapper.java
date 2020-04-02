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
package io.atomix.utils.config;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;
import io.atomix.utils.Named;
import io.atomix.utils.memory.MemorySize;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility for applying Typesafe configurations to Atomix configuration objects. */
public class ConfigMapper {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMapper.class);
  private final ClassLoader classLoader;

  public ConfigMapper(final ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /**
   * Loads the given configuration file using the mapper, falling back to the given resources.
   *
   * @param type the type to load
   * @param files the files to load
   * @param resources the resources to which to fall back
   * @param <T> the resulting type
   * @return the loaded configuration
   */
  public <T> T loadFiles(
      final Class<T> type, final List<File> files, final List<String> resources) {
    if (files == null) {
      return loadResources(type, resources);
    }

    Config config = ConfigFactory.systemProperties();
    for (final File file : files) {
      config =
          config.withFallback(
              ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)));
    }

    for (final String resource : resources) {
      config = config.withFallback(ConfigFactory.load(classLoader, resource));
    }
    return map(checkNotNull(config, "config cannot be null").resolve(), type);
  }

  /**
   * Loads the given resources using the configuration mapper.
   *
   * @param type the type to load
   * @param resources the resources to load
   * @param <T> the resulting type
   * @return the loaded configuration
   */
  public <T> T loadResources(final Class<T> type, final String... resources) {
    return loadResources(type, Arrays.asList(resources));
  }

  /**
   * Loads the given resources using the configuration mapper.
   *
   * @param type the type to load
   * @param resources the resources to load
   * @param <T> the resulting type
   * @return the loaded configuration
   */
  public <T> T loadResources(final Class<T> type, final List<String> resources) {
    if (resources == null || resources.isEmpty()) {
      throw new IllegalArgumentException("resources must be defined");
    }
    Config config = null;
    for (final String resource : resources) {
      if (config == null) {
        config = ConfigFactory.load(classLoader, resource);
      } else {
        config = config.withFallback(ConfigFactory.load(classLoader, resource));
      }
    }
    return map(checkNotNull(config, "config cannot be null").resolve(), type);
  }

  /**
   * Applies the given configuration to the given type.
   *
   * @param config the configuration to apply
   * @param clazz the class to which to apply the configuration
   */
  protected <T> T map(final Config config, final Class<T> clazz) {
    return map(config, null, null, clazz);
  }

  protected <T> T newInstance(final Config config, final String key, final Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (final InstantiationException | IllegalAccessException e) {
      throw new ConfigurationException(
          clazz.getName() + " needs a public no-args constructor to be used as a bean", e);
    }
  }

  /**
   * Applies the given configuration to the given type.
   *
   * @param config the configuration to apply
   * @param clazz the class to which to apply the configuration
   */
  @SuppressWarnings("unchecked")
  protected <T> T map(
      final Config config, final String path, final String name, final Class<T> clazz) {
    final T instance = newInstance(config, name, clazz);

    // Map config property names to bean properties.
    final Map<String, String> propertyNames = new HashMap<>();
    for (final Map.Entry<String, ConfigValue> configProp : config.root().entrySet()) {
      final String originalName = configProp.getKey();
      final String camelName = toCamelCase(originalName);
      // if a setting is in there both as some hyphen name and the camel name,
      // the camel one wins
      if (!propertyNames.containsKey(camelName) || originalName.equals(camelName)) {
        propertyNames.put(camelName, originalName);
      }
    }

    // First use setters and then fall back to fields.
    mapSetters(instance, clazz, path, name, propertyNames, config);
    mapFields(instance, clazz, path, name, propertyNames, config);

    // If any properties present in the configuration were not found on config beans, throw an
    // exception.
    if (!propertyNames.isEmpty()) {
      checkRemainingProperties(
          propertyNames.keySet(), describeProperties(instance), toPath(path, name), clazz);
    }
    return instance;
  }

  protected void checkRemainingProperties(
      final Set<String> missingProperties,
      final List<String> availableProperties,
      final String path,
      final Class<?> clazz) {
    final Properties properties = System.getProperties();
    final List<String> cleanNames =
        missingProperties.stream()
            .map(propertyName -> toPath(path, propertyName))
            .filter(propertyName -> !properties.containsKey(propertyName))
            .filter(
                propertyName ->
                    properties.entrySet().stream()
                        .noneMatch(
                            entry -> entry.getKey().toString().startsWith(propertyName + ".")))
            .sorted()
            .collect(Collectors.toList());
    if (!cleanNames.isEmpty()) {
      throw new ConfigurationException(
          "Unknown properties present in configuration: "
              + Joiner.on(", ").join(cleanNames)
              + "\n"
              + "Available properties:\n- "
              + Joiner.on("\n- ").join(availableProperties));
    }
  }

  private List<String> describeProperties(final Object instance) {
    final Stream<String> setters =
        getSetterDescriptors(instance.getClass()).stream().map(descriptor -> descriptor.name);
    final Stream<String> fields =
        getFieldDescriptors(instance.getClass()).stream().map(descriptor -> descriptor.name);
    return Stream.concat(setters, fields).sorted().collect(Collectors.toList());
  }

  private <T> void mapSetters(
      final T instance,
      final Class<T> clazz,
      final String path,
      final String name,
      final Map<String, String> propertyNames,
      final Config config) {
    try {
      for (final SetterDescriptor descriptor : getSetterDescriptors(instance.getClass())) {
        final Method setter = descriptor.setter;
        final Type parameterType = setter.getGenericParameterTypes()[0];
        final Class<?> parameterClass = setter.getParameterTypes()[0];

        final String configPropName = propertyNames.remove(descriptor.name);
        if (configPropName == null) {
          if ((Named.class.isAssignableFrom(clazz) || NamedConfig.class.isAssignableFrom(clazz))
              && descriptor.setter.getParameterTypes()[0] == String.class
              && name != null
              && descriptor.name.equals("name")) {
            if (descriptor.deprecated) {
              logDeprecatedWarning(path, name);
            }
            setter.invoke(instance, name);
          }
          continue;
        }

        final Object value =
            getValue(
                instance.getClass(),
                parameterType,
                parameterClass,
                config,
                toPath(path, name),
                configPropName);
        if (value != null) {
          if (descriptor.deprecated) {
            if (path == null) {
              LOGGER.warn("{}.{} is deprecated!", name, configPropName);
            } else {
              LOGGER.warn("{}.{}.{} is deprecated!", path, name, configPropName);
            }
          }
          setter.invoke(instance, value);
        }
      }
    } catch (final IllegalAccessException e) {
      throw new ConfigurationException(
          instance.getClass().getName()
              + " getters and setters are not accessible, they must be for use as a bean",
          e);
    } catch (final InvocationTargetException e) {
      throw new ConfigurationException(
          "Calling bean method on " + instance.getClass().getName() + " caused an exception", e);
    }
  }

  private void logDeprecatedWarning(final String path, final String name) {
    if (path == null) {
      LOGGER.warn("{} is deprecated!", name);
    } else {
      LOGGER.warn("{}.{} is deprecated!", path, name);
    }
  }

  private <T> void mapFields(
      final T instance,
      final Class<T> clazz,
      final String path,
      final String name,
      final Map<String, String> propertyNames,
      final Config config) {
    try {
      for (final FieldDescriptor descriptor : getFieldDescriptors(instance.getClass())) {
        final Field field = descriptor.field;
        field.setAccessible(true);

        final Type genericType = field.getGenericType();
        final Class<?> fieldClass = field.getType();

        final String configPropName = propertyNames.remove(descriptor.name);
        if (configPropName == null) {
          if (Named.class.isAssignableFrom(clazz)
              && field.getType() == String.class
              && name != null
              && descriptor.name.equals("name")) {
            if (descriptor.deprecated) {
              LOGGER.warn("{}.{} is deprecated!", path, name);
            }
            field.set(instance, name);
          }
          continue;
        }

        final Object value =
            getValue(
                instance.getClass(),
                genericType,
                fieldClass,
                config,
                toPath(path, name),
                configPropName);
        if (value != null) {
          if (descriptor.deprecated) {
            LOGGER.warn("{}.{} is deprecated!", path, name);
          }
          field.set(instance, value);
        }
      }
    } catch (final IllegalAccessException e) {
      throw new ConfigurationException(
          instance.getClass().getName()
              + " fields are not accessible, they must be for use as a bean",
          e);
    }
  }

  protected Object getValue(
      final Class<?> beanClass,
      final Type parameterType,
      final Class<?> parameterClass,
      final Config config,
      final String configPath,
      final String configPropName) {
    if (parameterClass == Boolean.class || parameterClass == boolean.class) {
      try {
        return config.getBoolean(configPropName);
      } catch (final ConfigException.WrongType e) {
        return Boolean.parseBoolean(config.getString(configPropName));
      }
    } else if (parameterClass == Integer.class || parameterClass == int.class) {
      try {
        return config.getInt(configPropName);
      } catch (final ConfigException.WrongType e) {
        try {
          return Integer.parseInt(config.getString(configPropName));
        } catch (final NumberFormatException e1) {
          throw e;
        }
      }
    } else if (parameterClass == Double.class || parameterClass == double.class) {
      try {
        return config.getDouble(configPropName);
      } catch (final ConfigException.WrongType e) {
        try {
          return Double.parseDouble(config.getString(configPropName));
        } catch (final NumberFormatException e1) {
          throw e;
        }
      }
    } else if (parameterClass == Long.class || parameterClass == long.class) {
      try {
        return config.getLong(configPropName);
      } catch (final ConfigException.WrongType e) {
        try {
          return Long.parseLong(config.getString(configPropName));
        } catch (final NumberFormatException e1) {
          throw e;
        }
      }
    } else if (parameterClass == String.class) {
      return config.getString(configPropName);
    } else if (parameterClass == Duration.class) {
      return config.getDuration(configPropName);
    } else if (parameterClass == MemorySize.class) {
      final ConfigMemorySize size = config.getMemorySize(configPropName);
      return new MemorySize(size.toBytes());
    } else if (parameterClass == Object.class) {
      return config.getAnyRef(configPropName);
    } else if (parameterClass == List.class || parameterClass == Collection.class) {
      return getListValue(
          beanClass, parameterType, parameterClass, config, configPath, configPropName);
    } else if (parameterClass == Set.class) {
      return getSetValue(
          beanClass, parameterType, parameterClass, config, configPath, configPropName);
    } else if (parameterClass == Map.class) {
      return getMapValue(
          beanClass, parameterType, parameterClass, config, configPath, configPropName);
    } else if (parameterClass == Config.class) {
      return config.getConfig(configPropName);
    } else if (parameterClass == ConfigObject.class) {
      return config.getObject(configPropName);
    } else if (parameterClass == ConfigValue.class) {
      return config.getValue(configPropName);
    } else if (parameterClass == ConfigList.class) {
      return config.getList(configPropName);
    } else if (parameterClass == Class.class) {
      final String className = config.getString(configPropName);
      try {
        return classLoader.loadClass(className);
      } catch (final ClassNotFoundException e) {
        throw new ConfigurationException("Failed to load class: " + className);
      }
    } else if (parameterClass.isEnum()) {
      final String value = config.getString(configPropName);
      final String enumName = value.replace("-", "_").toUpperCase();
      @SuppressWarnings("unchecked")
      final Enum enumValue = Enum.valueOf((Class<Enum>) parameterClass, enumName);
      try {
        final Deprecated deprecated =
            enumValue.getDeclaringClass().getField(enumName).getAnnotation(Deprecated.class);
        if (deprecated != null) {
          LOGGER.warn("{}.{} = {} is deprecated!", configPath, configPropName, value);
        }
      } catch (final NoSuchFieldException e) {
        // can happen
      }
      return enumValue;
    } else {
      return map(config.getConfig(configPropName), configPath, configPropName, parameterClass);
    }
  }

  protected Map getMapValue(
      final Class<?> beanClass,
      final Type parameterType,
      final Class<?> parameterClass,
      final Config config,
      final String configPath,
      final String configPropName) {
    final Type[] typeArgs = ((ParameterizedType) parameterType).getActualTypeArguments();
    final Type keyType = typeArgs[0];
    final Type valueType = typeArgs[1];

    final Map<Object, Object> map = new HashMap<>();
    final Config childConfig = config.getConfig(configPropName);
    final Class valueClass =
        (Class)
            (valueType instanceof ParameterizedType
                ? ((ParameterizedType) valueType).getRawType()
                : valueType);
    for (final String key : config.getObject(configPropName).unwrapped().keySet()) {
      final Object value =
          getValue(
              Map.class,
              valueType,
              valueClass,
              childConfig,
              toPath(configPath, configPropName),
              key);
      map.put(getKeyValue(keyType, key), value);
    }
    return map;
  }

  protected Object getKeyValue(final Type keyType, final String key) {
    if (keyType == Boolean.class || keyType == boolean.class) {
      return Boolean.parseBoolean(key);
    } else if (keyType == Integer.class || keyType == int.class) {
      return Integer.parseInt(key);
    } else if (keyType == Double.class || keyType == double.class) {
      return Double.parseDouble(key);
    } else if (keyType == Long.class || keyType == long.class) {
      return Long.parseLong(key);
    } else if (keyType == String.class) {
      return key;
    } else {
      throw new ConfigurationException("Invalid map key type: " + keyType);
    }
  }

  protected Object getSetValue(
      final Class<?> beanClass,
      final Type parameterType,
      final Class<?> parameterClass,
      final Config config,
      final String configPath,
      final String configPropName) {
    return new HashSet(
        (List)
            getListValue(
                beanClass, parameterType, parameterClass, config, configPath, configPropName));
  }

  protected Object getListValue(
      final Class<?> beanClass,
      final Type parameterType,
      final Class<?> parameterClass,
      final Config config,
      final String configPath,
      final String configPropName) {
    Type elementType = ((ParameterizedType) parameterType).getActualTypeArguments()[0];
    if (elementType instanceof ParameterizedType) {
      elementType = ((ParameterizedType) elementType).getRawType();
    }

    if (elementType == Boolean.class) {
      try {
        return config.getBooleanList(configPropName);
      } catch (final ConfigException.WrongType e) {
        return config.getStringList(configPropName).stream()
            .map(Boolean::parseBoolean)
            .collect(Collectors.toList());
      }
    } else if (elementType == Integer.class) {
      try {
        return config.getIntList(configPropName);
      } catch (final ConfigException.WrongType e) {
        return config.getStringList(configPropName).stream()
            .map(
                value -> {
                  try {
                    return Integer.parseInt(value);
                  } catch (NumberFormatException e2) {
                    throw e;
                  }
                })
            .collect(Collectors.toList());
      }
    } else if (elementType == Double.class) {
      try {
        return config.getDoubleList(configPropName);
      } catch (final ConfigException.WrongType e) {
        return config.getStringList(configPropName).stream()
            .map(
                value -> {
                  try {
                    return Double.parseDouble(value);
                  } catch (NumberFormatException e2) {
                    throw e;
                  }
                })
            .collect(Collectors.toList());
      }
    } else if (elementType == Long.class) {
      try {
        return config.getLongList(configPropName);
      } catch (final ConfigException.WrongType e) {
        return config.getStringList(configPropName).stream()
            .map(
                value -> {
                  try {
                    return Long.parseLong(value);
                  } catch (NumberFormatException e2) {
                    throw e;
                  }
                })
            .collect(Collectors.toList());
      }
    } else if (elementType == String.class) {
      return config.getStringList(configPropName);
    } else if (elementType == Duration.class) {
      return config.getDurationList(configPropName);
    } else if (elementType == MemorySize.class) {
      final List<ConfigMemorySize> sizes = config.getMemorySizeList(configPropName);
      return sizes.stream()
          .map(size -> new MemorySize(size.toBytes()))
          .collect(Collectors.toList());
    } else if (elementType == Class.class) {
      return config.getStringList(configPropName).stream()
          .map(
              className -> {
                try {
                  return classLoader.loadClass(className);
                } catch (ClassNotFoundException e) {
                  throw new ConfigurationException("Failed to load class: " + className);
                }
              })
          .collect(Collectors.toList());
    } else if (elementType == Object.class) {
      return config.getAnyRefList(configPropName);
    } else if (((Class<?>) elementType).isEnum()) {
      @SuppressWarnings("unchecked")
      final List<Enum> enumValues = config.getEnumList((Class<Enum>) elementType, configPropName);
      return enumValues;
    } else {
      final List<Object> beanList = new ArrayList<>();
      final List<? extends Config> configList = config.getConfigList(configPropName);
      final int i = 0;
      for (final Config listMember : configList) {
        beanList.add(
            map(
                listMember,
                toPath(configPath, configPropName),
                String.valueOf(i),
                (Class<?>) elementType));
      }
      return beanList;
    }
  }

  protected String toPath(final String path, final String name) {
    return path != null ? String.format("%s.%s", path, name) : name;
  }

  protected static boolean isSimpleType(final Class<?> parameterClass) {
    return parameterClass == Boolean.class
        || parameterClass == boolean.class
        || parameterClass == Integer.class
        || parameterClass == int.class
        || parameterClass == Double.class
        || parameterClass == double.class
        || parameterClass == Long.class
        || parameterClass == long.class
        || parameterClass == String.class
        || parameterClass == Duration.class
        || parameterClass == MemorySize.class
        || parameterClass == List.class
        || parameterClass == Map.class
        || parameterClass == Class.class;
  }

  protected static String toCamelCase(final String originalName) {
    final String[] words = originalName.split("-+");
    if (words.length > 1) {
      LOGGER.warn("Kebab case config name '" + originalName + "' is deprecated!");
      final StringBuilder nameBuilder = new StringBuilder(originalName.length());
      for (final String word : words) {
        if (nameBuilder.length() == 0) {
          nameBuilder.append(word);
        } else {
          nameBuilder.append(word.substring(0, 1).toUpperCase());
          nameBuilder.append(word.substring(1));
        }
      }
      return nameBuilder.toString();
    }
    return originalName;
  }

  protected static String toSetterName(final String name) {
    return "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  protected static Collection<SetterDescriptor> getSetterDescriptors(final Class<?> clazz) {
    final Map<String, SetterDescriptor> descriptors = Maps.newHashMap();
    for (final Method method : clazz.getMethods()) {
      String name = method.getName();
      if (method.getParameterTypes().length == 1
          && name.length() > 3
          && name.substring(0, 3).equals("set")
          && name.charAt(3) >= 'A'
          && name.charAt(3) <= 'Z') {

        // Strip the "set" prefix from the property name.
        name = method.getName().substring(3);
        name =
            name.length() > 1
                ? name.substring(0, 1).toLowerCase() + name.substring(1)
                : name.toLowerCase();

        // Strip the "Config" suffix from the property name.
        if (name.endsWith("Config")) {
          name = name.substring(0, name.length() - "Config".length());
        }

        // If a setter with this property name has already been registered, determine whether to
        // override it.
        // We favor simpler types over more complex types (i.e. beans).
        final SetterDescriptor descriptor = descriptors.get(name);
        if (descriptor != null) {
          final Class<?> type = method.getParameterTypes()[0];
          if (isSimpleType(type)) {
            descriptors.put(name, new SetterDescriptor(name, method));
          }
        } else {
          descriptors.put(name, new SetterDescriptor(name, method));
        }
      }
    }
    return descriptors.values();
  }

  protected static Collection<FieldDescriptor> getFieldDescriptors(final Class<?> type) {
    Class<?> clazz = type;
    final Map<String, FieldDescriptor> descriptors = Maps.newHashMap();
    while (clazz != Object.class) {
      for (final Field field : clazz.getDeclaredFields()) {
        // If the field is static or transient, ignore it.
        if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        // If the field has a setter, ignore it and use the setter.
        final Method method =
            Stream.of(clazz.getMethods())
                .filter(m -> m.getName().equals(toSetterName(field.getName())))
                .findFirst()
                .orElse(null);
        if (method != null) {
          continue;
        }

        // Strip the "Config" suffix from the field.
        String name = field.getName();
        if (name.endsWith("Config")) {
          name = name.substring(0, name.length() - "Config".length());
        }
        descriptors.putIfAbsent(name, new FieldDescriptor(name, field));
      }
      clazz = clazz.getSuperclass();
    }
    return Lists.newArrayList(descriptors.values());
  }

  protected static class SetterDescriptor {
    private final String name;
    private final Method setter;
    private final boolean deprecated;

    SetterDescriptor(final String name, final Method setter) {
      this.name = name;
      this.setter = setter;
      this.deprecated = setter.getAnnotation(Deprecated.class) != null;
    }
  }

  protected static class FieldDescriptor {
    private final String name;
    private final Field field;
    private final boolean deprecated;

    FieldDescriptor(final String name, final Field field) {
      this.name = name;
      this.field = field;
      this.deprecated = field.getAnnotation(Deprecated.class) != null;
    }
  }
}
