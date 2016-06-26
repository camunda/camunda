package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.impl.cmd.wf.deploy.DeployBpmnResourceAckResponseHandler;
import org.camunda.tngp.client.impl.cmd.wf.deploy.DeployBpmnResourceCmdImpl;
import org.camunda.tngp.client.impl.cmd.wf.deploy.DeployBpmnResourceRequestWriter;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;

public class DeployBpmnResourceCmdTest
{

    public static final int DEFAULT_SHARD_ID = 0;
    public static final int DEFAULT_RESOURCE_ID = 0;

    public static final byte[] RESOURCE = new byte[]{122, 52, 74};

    protected DeployBpmnResourceRequestWriter requestWriter;

    @Before
    public void setUp()
    {
        requestWriter = mock(DeployBpmnResourceRequestWriter.class);
    }

    @Test
    public void testSetResourceAsBytes()
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));
        command.setRequestWriter(requestWriter);
        final DeployBpmnResourceCmd apiCommand = command;

        // when
        apiCommand.resourceBytes(RESOURCE);

        // then
        verify(requestWriter).resource(RESOURCE);
    }

    @Test
    public void testSetResourceAsStream()
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));
        command.setRequestWriter(requestWriter);
        final DeployBpmnResourceCmd apiCommand = command;

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(RESOURCE);

        // when
        apiCommand.resourceStream(inputStream);

        // then
        verify(requestWriter).resource(RESOURCE);
    }

    @Test
    public void testSetResourceFromClasspath() throws IOException
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));
        command.setRequestWriter(requestWriter);
        final DeployBpmnResourceCmd apiCommand = command;

        // when
        apiCommand.resourceFromClasspath("example_file");

        // then
        final byte[] expectedBytes = readClasspathFile("example_file");

        verify(requestWriter).resource(expectedBytes);
    }

    @Test
    public void testSetResourceFromFile() throws IOException
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));
        command.setRequestWriter(requestWriter);
        final DeployBpmnResourceCmd apiCommand = command;

        final ClassLoader classLoader = DeployBpmnResourceCmdTest.class.getClassLoader();
        final URL fileUrl = classLoader.getResource("example_file");

        // when
        apiCommand.resourceFile(fileUrl.getFile());

        // then
        final byte[] expectedBytes = readClasspathFile("example_file");

        verify(requestWriter).resource(expectedBytes);
    }

    @Test
    public void testSetResourceAsModelInstance()
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));
        command.setRequestWriter(requestWriter);
        final DeployBpmnResourceCmd apiCommand = command;

        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("foo")
                .startEvent().endEvent().done();

        // when
        apiCommand.bpmnModelInstance(modelInstance);

        // then
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        Bpmn.writeModelToStream(byteStream, modelInstance);

        verify(requestWriter).resource(byteStream.toByteArray());
    }

    @Test
    public void testRequestWriter()
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));

        // when
        final BufferWriter requestWriter = command.getRequestWriter();

        // then
        assertThat(requestWriter).isInstanceOf(DeployBpmnResourceRequestWriter.class);
    }

    @Test
    public void testResponseHandlers()
    {
        // given
        final DeployBpmnResourceCmdImpl command = new DeployBpmnResourceCmdImpl(mock(ClientCmdExecutor.class));

        // when
        final ClientResponseHandler<DeployedWorkflowType> responseHandler = command.getResponseHandler();

        // then
        assertThat(responseHandler).isInstanceOf(DeployBpmnResourceAckResponseHandler.class);
    }

    protected byte[] readClasspathFile(final String path) throws IOException
    {
        final ClassLoader classLoader = DeployBpmnResourceCmdTest.class.getClassLoader();
        final InputStream resourceStream = classLoader.getResourceAsStream(path);
        final BufferedInputStream bufferedStream = new BufferedInputStream(resourceStream);

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        int nextByte;
        while ((nextByte = bufferedStream.read()) >= 0)
        {
            byteStream.write((byte) nextByte);
        }

        bufferedStream.close();

        return byteStream.toByteArray();
    }
}
