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
package io.zeebe.gateway.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.DeploymentCommand;
import io.zeebe.gateway.api.commands.IncidentCommand;
import io.zeebe.gateway.api.commands.JobCommand;
import io.zeebe.gateway.api.commands.WorkflowInstanceCommand;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.IncidentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.RaftEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.api.record.ZeebeObjectMapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ZeebeObjectMapperRecordSerializationTest {
  private static final String DIRECTORY = "/json/";

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"JobEvent.json", JobEvent.class},
          {"JobCommand.json", JobCommand.class},
          {"WorkflowInstanceEvent.json", WorkflowInstanceEvent.class},
          {"WorkflowInstanceCommand.json", WorkflowInstanceCommand.class},
          {"DeploymentEvent.json", DeploymentEvent.class},
          {"DeploymentCommand.json", DeploymentCommand.class},
          {"DeploymentCommandRejection.json", DeploymentCommand.class},
          {"IncidentEvent.json", IncidentEvent.class},
          {"IncidentCommand.json", IncidentCommand.class},
          {"RaftEvent.json", RaftEvent.class}
        });
  }

  @Parameter(0)
  public String recordFile;

  @Parameter(1)
  public Class<? extends Record> recordClass;

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  private ZeebeObjectMapper objectMapper;
  private final ObjectMapper testObjectMapper = new ObjectMapper();

  @Before
  public void init() {
    final ZeebeClient client = ZeebeClient.newClient();
    closeables.manage(client);

    this.objectMapper = client.objectMapper();
  }

  @Test
  public void shouldSerializeAndDeserializeRecord() throws Exception {
    final String json = readFileContent(DIRECTORY + recordFile);

    final Record deserialiedRecord = objectMapper.fromJson(json, recordClass);
    final String serializedRecord = objectMapper.toJson(deserialiedRecord);

    assertThat(readAsMap(serializedRecord)).containsOnly(asEntries(readAsMap(json)));
  }

  private String readFileContent(final String file) throws IOException, URISyntaxException {
    final Path path = Paths.get(getClass().getResource(file).toURI());
    final byte[] bytes = Files.readAllBytes(path);
    return new String(bytes);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readAsMap(final String json) throws Exception {
    return testObjectMapper.readValue(json, Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map.Entry<String, Object>[] asEntries(final Map<String, Object> map) {
    return map.entrySet().toArray(new Map.Entry[map.size()]);
  }
}
