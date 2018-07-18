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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_PARALLEL_GATEWAY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_ASYNC;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ParallelGatewayBuilder;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN parallelGateway element
 *
 * @author Sebastian Menski
 */
public class ParallelGatewayImpl extends GatewayImpl implements ParallelGateway {

  protected static Attribute<Boolean> camundaAsyncAttribute;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ParallelGateway.class, BPMN_ELEMENT_PARALLEL_GATEWAY)
            .namespaceUri(BPMN20_NS)
            .extendsType(Gateway.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ParallelGateway>() {
                  @Override
                  public ParallelGateway newInstance(ModelTypeInstanceContext instanceContext) {
                    return new ParallelGatewayImpl(instanceContext);
                  }
                });

    /** camunda extensions */
    camundaAsyncAttribute =
        typeBuilder
            .booleanAttribute(CAMUNDA_ATTRIBUTE_ASYNC)
            .namespace(CAMUNDA_NS)
            .defaultValue(false)
            .build();

    typeBuilder.build();
  }

  @Override
  public ParallelGatewayBuilder builder() {
    return new ParallelGatewayBuilder((BpmnModelInstance) modelInstance, this);
  }

  /** camunda extensions */

  /** @deprecated use isCamundaAsyncBefore() instead. */
  @Override
  @Deprecated
  public boolean isCamundaAsync() {
    return camundaAsyncAttribute.getValue(this);
  }

  /** @deprecated use setCamundaAsyncBefore(isCamundaAsyncBefore) instead. */
  @Override
  @Deprecated
  public void setCamundaAsync(boolean isCamundaAsync) {
    camundaAsyncAttribute.setValue(this, isCamundaAsync);
  }

  public ParallelGatewayImpl(ModelTypeInstanceContext context) {
    super(context);
  }
}
