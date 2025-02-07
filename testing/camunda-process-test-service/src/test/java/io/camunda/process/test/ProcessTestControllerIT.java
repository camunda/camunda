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
package io.camunda.process.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = Application.class)
@AutoConfigureMockMvc
public class ProcessTestControllerIT {

  @Autowired private MockMvc mvc;

  @Test
  void shouldExecuteTest() throws Exception {
    // given
    final URL resource = Objects.requireNonNull(getClass().getResource("/request-body.txt"));
    final String requestBody = Files.readString(Path.of(resource.toURI()));

    // when/then
    mvc.perform(
            post("/process-tests/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.passesTestCases").value(1))
        .andExpect(jsonPath("$.totalTestCases").value(2))
        .andExpect(jsonPath("$.totalTestDuration").exists())
        .andExpect(jsonPath("$.testResults", hasSize(2)))
        .andExpect(jsonPath("$.testResults[0].name").value("case-1"))
        .andExpect(jsonPath("$.testResults[0].success").value(true))
        .andExpect(jsonPath("$.testResults[0].failedInstruction").isEmpty())
        .andExpect(jsonPath("$.testResults[0].failureMessage").isEmpty())
        .andExpect(jsonPath("$.testResults[0].testDuration").exists())
        .andExpect(jsonPath("$.testResults[0].testOutput").isEmpty())
        .andExpect(jsonPath("$.testResults[1].name").value("case-2"))
        .andExpect(jsonPath("$.testResults[1].success").value(false))
        .andExpect(
            jsonPath("$.testResults[1].failedInstruction.name")
                .value("verify-process-instance-state"))
        .andExpect(
            jsonPath(
                "$.testResults[1].failureMessage",
                containsString("should be completed but was active")))
        .andExpect(jsonPath("$.testResults[1].testDuration").exists())
        .andExpect(jsonPath("$.testResults[1].testOutput").isArray());
  }
}
