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
package io.camunda.process.test.impl.spec;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.ProcessInstanceAssertj;
import java.util.HashMap;
import java.util.Map;

public class SpecTestContext {

  private final CamundaDataSource dataSource;
  private final CamundaClient camundaClient;

  private final Map<String, Long> processInstanceKeyByAlias = new HashMap<>();

  public SpecTestContext(final CamundaDataSource dataSource, final CamundaClient camundaClient) {
    this.dataSource = dataSource;
    this.camundaClient = camundaClient;
  }

  public void putProcessInstanceWithAlias(final String alias, final long processInstanceKey) {
    processInstanceKeyByAlias.put(alias, processInstanceKey);
  }

  public ProcessInstanceAssert assertThatProcessInstance(final String alias) {
    if (!processInstanceKeyByAlias.containsKey(alias)) {
      throw new RuntimeException(
          String.format("No process instance found with alias: '%s'", alias));
    }

    final Long processInstanceKey = processInstanceKeyByAlias.get(alias);
    return new ProcessInstanceAssertj(dataSource, processInstanceKey, ElementSelectors::byId);
  }

  public CamundaClient getCamundaClient() {
    return camundaClient;
  }
}
