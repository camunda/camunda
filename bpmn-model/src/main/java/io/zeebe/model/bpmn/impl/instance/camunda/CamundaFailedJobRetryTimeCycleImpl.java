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

package io.zeebe.model.bpmn.impl.instance.camunda;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_FAILED_JOB_RETRY_TIME_CYCLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaFailedJobRetryTimeCycle;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;

/**
 * The BPMN failedJobRetryTimeCycle camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaFailedJobRetryTimeCycleImpl extends BpmnModelElementInstanceImpl
    implements CamundaFailedJobRetryTimeCycle {

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(
                CamundaFailedJobRetryTimeCycle.class, CAMUNDA_ELEMENT_FAILED_JOB_RETRY_TIME_CYCLE)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaFailedJobRetryTimeCycle>() {
                  @Override
                  public CamundaFailedJobRetryTimeCycle newInstance(
                      ModelTypeInstanceContext instanceContext) {
                    return new CamundaFailedJobRetryTimeCycleImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }

  public CamundaFailedJobRetryTimeCycleImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }
}
