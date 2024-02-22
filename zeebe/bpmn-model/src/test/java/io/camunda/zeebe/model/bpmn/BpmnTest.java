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

package io.camunda.zeebe.model.bpmn;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_EXECUTION_PLATFORM;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_EXPORTER;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.MODELER_NS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.util.VersionUtil;
import org.junit.Test;

/**
 * @author Sebastian Menski
 */
public class BpmnTest {

  @Test
  public void testBpmn() {
    assertThat(Bpmn.INSTANCE).isNotNull();
  }

  @Test
  public void testBpmnWithDefinitions() {
    final BpmnModelInstance model = Bpmn.createProcess().startEvent().done();
    final Definitions definitions = model.getDefinitions();
    assertThat(definitions.getExporter()).isEqualTo(BPMN_EXPORTER);
    assertThat(definitions.getExporterVersion()).isEqualTo(VersionUtil.getVersion());
    assertThat(definitions.getAttributeValueNs(MODELER_NS, "executionPlatform"))
        .isEqualTo(BPMN_EXECUTION_PLATFORM);
    assertThat(definitions.getAttributeValueNs(MODELER_NS, "executionPlatformVersion"))
        .isEqualTo(VersionUtil.getVersion());
  }
}
