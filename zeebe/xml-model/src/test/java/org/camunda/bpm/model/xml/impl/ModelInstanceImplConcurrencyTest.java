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
package org.camunda.bpm.model.xml.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.util.DomUtil;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

/**
 * All model instances of a model share its model builder. Reading an element whose type the model
 * doesn't know adds a generic type through that builder, and model instances are read on different
 * threads at the same time.
 */
final class ModelInstanceImplConcurrencyTest {

  private static final int THREAD_COUNT = 16;
  private static final int TYPE_COUNT = 100;
  private static final String NAMESPACE = "urn:model-instance-concurrency-test";

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(THREAD_COUNT);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    executor.shutdownNow();
    executor.awaitTermination(30, TimeUnit.SECONDS);
  }

  @RepeatedTest(5)
  void shouldAddEachGenericTypeOnceWhenAddedFromManyThreads() throws Exception {
    // given - one model instance per thread, all sharing a new model and its model builder
    final ModelBuilder modelBuilder = ModelBuilder.createInstance("concurrency test");
    final ModelImpl model = (ModelImpl) modelBuilder.build();
    final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    final List<ModelInstanceImpl> modelInstances = new ArrayList<ModelInstanceImpl>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      modelInstances.add(
          new ModelInstanceImpl(
              model, modelBuilder, DomUtil.getEmptyDocument(documentBuilderFactory)));
    }

    // when - all threads add the same generic types at the same time
    final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    final List<Future<List<ModelElementType>>> futures =
        new ArrayList<Future<List<ModelElementType>>>();
    for (final ModelInstanceImpl modelInstance : modelInstances) {
      final Callable<List<ModelElementType>> registerGenericTypes =
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            final List<ModelElementType> types = new ArrayList<ModelElementType>();
            for (int i = 0; i < TYPE_COUNT; i++) {
              types.add(modelInstance.registerGenericType(NAMESPACE, "element" + i));
            }
            return types;
          };
      futures.add(executor.submit(registerGenericTypes));
    }

    // then - every thread got the one type that the model has for each name
    for (final Future<List<ModelElementType>> future : futures) {
      final List<ModelElementType> types = future.get(30, TimeUnit.SECONDS);
      for (int i = 0; i < TYPE_COUNT; i++) {
        assertThat(types.get(i))
            .describedAs("type of element%d", i)
            .isNotNull()
            .isSameAs(model.getTypeForName(NAMESPACE, "element" + i));
      }
    }
  }
}
