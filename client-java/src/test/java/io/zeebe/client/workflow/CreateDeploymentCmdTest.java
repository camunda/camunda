/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.protocol.clientapi.EventType.DEPLOYMENT_EVENT;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.ClientResponseHandler;
import io.zeebe.client.workflow.cmd.DeploymentResult;
import io.zeebe.client.workflow.impl.CreateDeploymentCmdImpl;
import io.zeebe.client.workflow.impl.DeployedWorkflow;
import io.zeebe.client.workflow.impl.DeploymentEvent;
import io.zeebe.client.workflow.impl.DeploymentEventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class CreateDeploymentCmdTest
{
    protected static final String TOPIC_NAME = "test-topic";
    private static final int PARTITION_ID = 1;
    private static final byte[] BUFFER = new byte[1014 * 1024];

    private static final BpmnModelInstance BPMN_MODEL_INSTANCE = Bpmn.createExecutableProcess("process")
            .startEvent()
            .done();

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandRequestDecoder requestDecoder = new ExecuteCommandRequestDecoder();
    private final ExecuteCommandResponseEncoder responseEncoder = new ExecuteCommandResponseEncoder();

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

    private ObjectMapper objectMapper;

    private CreateDeploymentCmdImpl command;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        final ClientCommandManager commandManager = mock(ClientCommandManager.class);

        objectMapper = new ObjectMapper(new MessagePackFactory());

        command = new CreateDeploymentCmdImpl(commandManager, objectMapper, new Topic(TOPIC_NAME, PARTITION_ID));

        writeBuffer.wrap(BUFFER);
    }

    @Test
    public void shouldWriteRequest() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        command
            .resourceString("myProcess");

        // when
        command.getRequestWriter().write(writeBuffer, 0);

        // then
        headerDecoder.wrap(writeBuffer, 0);

        assertThat(headerDecoder.schemaId()).isEqualTo(requestDecoder.sbeSchemaId());
        assertThat(headerDecoder.version()).isEqualTo(requestDecoder.sbeSchemaVersion());
        assertThat(headerDecoder.templateId()).isEqualTo(requestDecoder.sbeTemplateId());
        assertThat(headerDecoder.blockLength()).isEqualTo(requestDecoder.sbeBlockLength());

        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        assertThat(requestDecoder.eventType()).isEqualTo(DEPLOYMENT_EVENT);
        assertThat(requestDecoder.topicName()).isEqualTo(TOPIC_NAME);
        assertThat(requestDecoder.partitionId()).isEqualTo(PARTITION_ID);

        final byte[] command = readBytes(requestDecoder::getCommand, requestDecoder::commandLength);

        final DeploymentEvent deploymentEvent = objectMapper.readValue(command, DeploymentEvent.class);

        assertThat(deploymentEvent.getEventType()).isEqualTo(DeploymentEventType.CREATE_DEPLOYMENT);
        assertThat(deploymentEvent.getBpmnXml()).isEqualTo("myProcess");
    }

    @Test
    public void shouldDeployBpmnModelInstanceRequest() throws IOException
    {
        // given
        command
            .bpmnModelInstance(BPMN_MODEL_INSTANCE);

        // when
        final DeploymentEvent deploymentEvent = writeCommand(command);

        // then
        assertThat(deploymentEvent.getBpmnXml()).isEqualTo(Bpmn.convertToString(BPMN_MODEL_INSTANCE));
    }

    @Test
    public void shouldDeployResourceFromStreamRequest() throws IOException
    {
        // given
        final String bpmnXml = Bpmn.convertToString(BPMN_MODEL_INSTANCE);
        final ByteArrayInputStream resourceStream = new ByteArrayInputStream(getBytes(bpmnXml));

        command
            .resourceStream(resourceStream);

        // when
        final DeploymentEvent deploymentEvent = writeCommand(command);

        // then
        assertThat(deploymentEvent.getBpmnXml()).isEqualTo(bpmnXml);
    }

    @Test
    public void shouldDeployResourceFromFileRequest() throws JsonParseException, JsonMappingException, IOException, URISyntaxException
    {
        // given
        final String filePath = getClass().getResource("/workflow/one-task-process.bpmn").toURI().getPath();

        command
            .resourceFile(filePath);

        // when
        final DeploymentEvent deploymentEvent = writeCommand(command);

        // then
        assertThat(deploymentEvent.getBpmnXml()).isXmlEqualToContentOf(new File(filePath));
    }

    @Test
    public void shouldDeployResourceFromClasspathRequest() throws JsonParseException, JsonMappingException, IOException, URISyntaxException
    {
        // given
        command
            .resourceFromClasspath("workflow/one-task-process.bpmn");

        // when
        final DeploymentEvent deploymentEvent = writeCommand(command);

        // then
        final String filePath = getClass().getResource("/workflow/one-task-process.bpmn").toURI().getPath();
        assertThat(deploymentEvent.getBpmnXml()).isXmlEqualToContentOf(new File(filePath));
    }

    @Test
    public void shouldReadDeployedResponse() throws JsonProcessingException
    {
        final ClientResponseHandler<DeploymentResult> responseHandler = command.getResponseHandler();

        assertThat(responseHandler.getResponseSchemaId()).isEqualTo(responseEncoder.sbeSchemaId());
        assertThat(responseHandler.getResponseTemplateId()).isEqualTo(responseEncoder.sbeTemplateId());

        responseEncoder.wrap(writeBuffer, 0);

        // given
        final DeploymentEvent deploymentEvent = new DeploymentEvent();
        deploymentEvent.setEventType(DeploymentEventType.DEPLOYMENT_CREATED);
        deploymentEvent.setErrorMessage("foo");

        final DeployedWorkflow deployedWorkflow1 = new DeployedWorkflow();
        deployedWorkflow1.setBpmnProcessId("p1");
        deployedWorkflow1.setVersion(1);

        final DeployedWorkflow deployedWorkflow2 = new DeployedWorkflow();
        deployedWorkflow2.setBpmnProcessId("p2");
        deployedWorkflow2.setVersion(2);

        deploymentEvent.setDeployedWorkflows(Arrays.asList(deployedWorkflow1, deployedWorkflow2));

        final byte[] jsonEvent = objectMapper.writeValueAsBytes(deploymentEvent);

        responseEncoder
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(2L)
            .putEvent(jsonEvent, 0, jsonEvent.length);

        // when
        final DeploymentResult deploymentResult = responseHandler.readResponse(writeBuffer, 0, responseEncoder.sbeBlockLength(), responseEncoder.sbeSchemaVersion());

        // then
        assertThat(deploymentResult.getKey()).isEqualTo(2L);
        assertThat(deploymentResult.getErrorMessage()).isEqualTo("foo");
        assertThat(deploymentResult.isDeployed()).isTrue();

        assertThat(deploymentResult.getDeployedWorkflows()).hasSize(2);
        assertThat(deploymentResult.getDeployedWorkflows()).extracting("bpmnProcessId").contains("p1", "p2");
        assertThat(deploymentResult.getDeployedWorkflows()).extracting("version").contains(1, 2);
    }

    @Test
    public void shouldReadRejectedResponse() throws JsonProcessingException
    {
        responseEncoder.wrap(writeBuffer, 0);

        // given
        final DeploymentEvent deploymentEvent = new DeploymentEvent();
        deploymentEvent.setEventType(DeploymentEventType.DEPLOYMENT_REJECTED);
        deploymentEvent.setErrorMessage("error");

        final byte[] jsonEvent = objectMapper.writeValueAsBytes(deploymentEvent);

        responseEncoder
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(2L)
            .putEvent(jsonEvent, 0, jsonEvent.length);

        // when
        final DeploymentResult deploymentResult = command.getResponseHandler().readResponse(writeBuffer, 0, responseEncoder.sbeBlockLength(), responseEncoder.sbeSchemaVersion());

        // then
        assertThat(deploymentResult.getErrorMessage()).isEqualTo("error");
        assertThat(deploymentResult.isDeployed()).isFalse();
    }

    @Test
    public void shouldBeNotValidIfNoResourceSet()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("resource must not be null");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfResourceFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from file");

        command
            .resourceFile("not existing");
    }

    @Test
    public void shouldBeNotValidIfResourceClasspathFileNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot deploy resource from classpath");

        command
            .resourceFromClasspath("not existing");
    }

    @Test
    public void shouldBeNotValidIfResourceStreamNotExist()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("resource stream must not be null");

        command
            .resourceStream(getClass().getResourceAsStream("not existing"));
    }

    private DeploymentEvent writeCommand(CreateDeploymentCmdImpl command) throws IOException, JsonParseException, JsonMappingException
    {
        command.getRequestWriter().write(writeBuffer, 0);

        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        // skip topic name
        requestDecoder.topicName();

        final byte[] buffer = readBytes(requestDecoder::getCommand, requestDecoder::commandLength);

        return objectMapper.readValue(buffer, DeploymentEvent.class);
    }

}
