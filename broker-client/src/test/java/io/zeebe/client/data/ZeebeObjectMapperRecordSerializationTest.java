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
package io.zeebe.client.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
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
          {"TopicEvent.json", TopicEvent.class},
          {"TopicCommand.json", TopicCommand.class},
          {"RaftEvent.json", RaftEvent.class}
        });
  }

  @Parameter(0)
  public String recordFile;

  @Parameter(1)
  public Class<? extends Record> recordClass;

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  private ZeebeObjectMapper objectMapper;
  private ObjectMapper testObjectMapper = new ObjectMapper();

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

  private String readFileContent(String file) throws IOException, URISyntaxException {
    final Path path = Paths.get(getClass().getResource(file).toURI());
    final byte[] bytes = Files.readAllBytes(path);
    return new String(bytes);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readAsMap(String json) throws Exception {
    return testObjectMapper.readValue(json, Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map.Entry<String, Object>[] asEntries(Map<String, Object> map) {
    return map.entrySet().toArray(new Map.Entry[map.size()]);
  }
}
