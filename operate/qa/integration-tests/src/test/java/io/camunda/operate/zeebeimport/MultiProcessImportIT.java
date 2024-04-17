/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MultiProcessImportIT extends OperateZeebeSearchAbstractIT {

  @Autowired private ProcessIndex processIndex;

  @Test
  public void shouldReadFromMultiProcessDiagram() throws IOException {

    // given
    final String bpmnFile = "multi-process.bpmn";
    operateTester.deployProcessAndWait(bpmnFile);

    // when
    final List<ProcessEntity> processEntities =
        testSearchRepository.searchAll(processIndex.getAlias(), ProcessEntity.class);
    final ProcessEntity process1 =
        processEntities.stream()
            .filter(x -> "process1".equals(x.getBpmnProcessId()))
            .findAny()
            .orElse(null);
    final ProcessEntity process2 =
        processEntities.stream()
            .filter(x -> "process2".equals(x.getBpmnProcessId()))
            .findAny()
            .orElse(null);

    // then
    assertThat(processEntities).hasSize(2);

    assertThat(process1).isNotNull();
    assertThat(process1.getName()).isEqualTo("Process 1");
    assertThat(process1.getResourceName()).isEqualTo(bpmnFile);
    assertThat(process1.getFlowNodes())
        .extracting("id")
        .containsExactlyInAnyOrder("startEvent1", "task1", "endEvent1");
    assertThat(process1.getFlowNodes())
        .extracting("name")
        .containsExactlyInAnyOrder("Start Event 1", "Task 1", "End Event 1");

    assertThat(process2).isNotNull();
    assertThat(process2.getName()).isEqualTo("Process 2");
    assertThat(process2.getResourceName()).isEqualTo(bpmnFile);
    assertThat(process2.getFlowNodes())
        .extracting("id")
        .containsExactlyInAnyOrder("startEvent2", "task2", "endEvent2");
    assertThat(process2.getFlowNodes())
        .extracting("name")
        .containsExactlyInAnyOrder("Start Event 2", "Task 2", "End Event 2");
  }
}
