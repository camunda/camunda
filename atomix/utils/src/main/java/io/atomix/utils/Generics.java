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
package io.atomix.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Generics utility. */
public final class Generics {

  private Generics() {}

  /**
   * Returns the generic type at the given position for the given class.
   *
   * @param instance the implementing instance
   * @param clazz the generic class
   * @param position the generic position
   * @return the generic type at the given position
   */
  public static Type getGenericClassType(
      final Object instance, final Class<?> clazz, final int position) {
    Class<?> type = instance.getClass();
    while (type != Object.class) {
      if (type.getGenericSuperclass() instanceof ParameterizedType) {
        final ParameterizedType genericSuperclass = (ParameterizedType) type.getGenericSuperclass();
        if (genericSuperclass.getRawType() == clazz) {
          return genericSuperclass.getActualTypeArguments()[position];
        } else {
          type = type.getSuperclass();
        }
      } else {
        type = type.getSuperclass();
      }
    }
    return null;
  }

  /**
   * Returns the generic type at the given position for the given interface.
   *
   * @param instance the implementing instance
   * @param iface the generic interface
   * @param position the generic position
   * @return the generic type at the given position
   */
  public static Type getGenericInterfaceType(
      final Object instance, final Class<?> iface, final int position) {
    Class<?> type = instance.getClass();
    while (type != Object.class) {
      for (final Type genericType : type.getGenericInterfaces()) {
        if (genericType instanceof ParameterizedType) {
          final ParameterizedType parameterizedType = (ParameterizedType) genericType;
          if (parameterizedType.getRawType() == iface) {
            return parameterizedType.getActualTypeArguments()[position];
          }
        }
      }
      type = type.getSuperclass();
    }
    return null;
  }
}
