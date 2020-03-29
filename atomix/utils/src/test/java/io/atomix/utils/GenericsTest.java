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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Generics test. */
public class GenericsTest {
  @Test
  public void testGetInterfaceType() throws Exception {
    assertEquals(
        String.class,
        Generics.getGenericInterfaceType(new ConcreteInterface(), GenericInterface.class, 0));
    assertEquals(
        SomeClass.class,
        Generics.getGenericInterfaceType(new ConcreteInterface(), GenericInterface.class, 1));
  }

  @Test
  public void testGetClassType() throws Exception {
    assertEquals(
        SomeClass.class, Generics.getGenericClassType(new ConcreteClass(), GenericClass.class, 0));
  }

  public static class ConcreteInterface implements GenericInterface<String, SomeClass> {
    @Override
    public String type1() {
      return null;
    }

    @Override
    public SomeClass type2() {
      return null;
    }
  }

  public interface GenericInterface<T1, T2> {
    T1 type1();

    T2 type2();
  }

  public abstract class GenericClass<T> {
    public abstract T type();
  }

  public class ConcreteClass extends GenericClass<SomeClass> {
    @Override
    public SomeClass type() {
      return null;
    }
  }

  public class SomeClass {}
}
