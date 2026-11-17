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
package io.camunda.zeebe.model.bpmn.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Every parsed model instance shares the model and model builder of {@link Bpmn#INSTANCE}. Reading
 * an extension element whose type the model doesn't know registers that type on the shared model
 * builder, which partitions and exporters on the same broker do from different threads.
 */
final class BpmnModelInstanceImplConcurrencyTest {

  private static final int THREAD_COUNT = 16;

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
  void shouldReadUnknownExtensionElementsConcurrently() throws Exception {
    // given - models parsed up front, each with an extension element of a type the shared model
    // doesn't know yet, so that every thread has to register a new type
    final List<String> namespaces = new ArrayList<>();
    final List<BpmnModelInstance> models = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      final String namespace = "http://camunda.io/test/" + UUID.randomUUID();
      namespaces.add(namespace);
      models.add(readModel(namespace));
    }

    // when - all threads read their extension elements at the same time
    final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    final List<Future<List<ModelElementInstance>>> futures = new ArrayList<>();
    for (final BpmnModelInstance model : models) {
      final Callable<List<ModelElementInstance>> readExtensionElements =
          () -> {
            final StartEvent startEvent = model.getModelElementById("start");
            barrier.await(10, TimeUnit.SECONDS);
            return new ArrayList<>(startEvent.getExtensionElements().getElements());
          };
      futures.add(executor.submit(readExtensionElements));
    }

    // then
    final List<Throwable> errors = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      try {
        final List<ModelElementInstance> elements = futures.get(i).get(30, TimeUnit.SECONDS);
        assertThat(elements)
            .singleElement()
            .satisfies(
                element -> {
                  assertThat(element.getDomElement().getLocalName()).isEqualTo("annotation");
                  assertThat(element.getElementType().getTypeName()).isEqualTo("annotation");
                });
      } catch (final ExecutionException e) {
        errors.add(e.getCause());
      }
    }
    assertThat(errors).isEmpty();
    for (int i = 0; i < THREAD_COUNT; i++) {
      assertThat(Bpmn.INSTANCE.getBpmnModel().getTypeForName(namespaces.get(i), "annotation"))
          .describedAs("type registered for namespace %s", namespaces.get(i))
          .isNotNull();
    }
  }

  private static BpmnModelInstance readModel(final String extensionNamespace) {
    final String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " xmlns:custom=\""
            + extensionNamespace
            + "\" targetNamespace=\"http://bpmn.io/schema/bpmn\">"
            + "<process id=\"process\" isExecutable=\"true\">"
            + "<startEvent id=\"start\">"
            + "<extensionElements><custom:annotation>test</custom:annotation></extensionElements>"
            + "</startEvent>"
            + "</process>"
            + "</definitions>";
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
