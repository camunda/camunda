package org.camunda.tngp.broker.wf.runtime.log.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.broker.wf.WfErrors;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionRequestReader;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.protocol.error.ErrorReader;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckResponseReader;
import org.camunda.tngp.protocol.log.WfDefinitionRequestType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class WfDefinitionRequestHandlerTest
{
    protected StubLogWriter logWriter;

    protected StubLogReader logReader;
    protected IdGenerator idGenerator = new PrivateIdGenerator(10L);
    protected StubResponseControl responseControl;
    protected StubLogWriters logWriters;


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
        logWriter = new StubLogWriter();
        responseControl = new StubResponseControl();
        logWriters = new StubLogWriters(0);
        logWriters.addWriter(0, logWriter);
    }

    @Test
    public void shouldDeployInitialVersion()
    {
        // given
        final String procesId = "someProcessId";
        final byte[] processIdBytes = procesId.getBytes(StandardCharsets.UTF_8);

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());

        final WfDefinitionRequestReader requestReader = mock(WfDefinitionRequestReader.class);
        when(requestReader.resource()).thenReturn(new UnsafeBuffer(resource));
        when(requestReader.type()).thenReturn(WfDefinitionRequestType.NEW);

        final WfDefinitionRequestHandler handler = new WfDefinitionRequestHandler(logReader, idGenerator);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(logWriters.writtenEntries()).isEqualTo(1);
        assertThat(logWriter.size()).isEqualTo(1);

        final WfDefinitionReader wfDefinitionReader = logWriter.getEntryAs(0, WfDefinitionReader.class);
        assertThat(wfDefinitionReader.id()).isEqualTo(11L);
        assertThatBuffer(wfDefinitionReader.getTypeKey()).hasBytes(processIdBytes);
        assertThatBuffer(wfDefinitionReader.getResource()).hasBytes(resource);

        assertThat(responseControl.size()).isEqualTo(1);

        final DeployBpmnResourceAckResponseReader responseReader = responseControl.getAcceptanceValueAs(0, DeployBpmnResourceAckResponseReader.class);
        assertThat(responseReader.wfDefinitionId()).isEqualTo(11L);
    }

    @Test
    public void shouldRejectInvalidProcess()
    {
        // given
        final byte[] resource = "not-bpmn".getBytes(StandardCharsets.UTF_8);

        final WfDefinitionRequestHandler handler = new WfDefinitionRequestHandler(logReader, idGenerator);

        final WfDefinitionRequestReader requestReader = mock(WfDefinitionRequestReader.class);
        when(requestReader.resource()).thenReturn(new UnsafeBuffer(resource));
        when(requestReader.type()).thenReturn(WfDefinitionRequestType.NEW);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(responseControl.size()).isEqualTo(1);

        final ErrorReader errorReader = responseControl.getRejectionValueAs(0, ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(WfErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(WfErrors.DEPLOYMENT_ERROR);
        assertThat(errorReader.errorMessage()).startsWith("Cannot deploy Bpmn Resource: Exception during parsing");

        assertThat(logWriters.writtenEntries()).isZero();
    }

    @Test
    public void shouldRejectIfIdExceedsMaxLength()
    {
        String procesId = "some-l";

        while (procesId.getBytes(StandardCharsets.UTF_8).length < Constants.WF_DEF_KEY_MAX_LENGTH)
        {
            procesId += "o";
        }

        procesId += "ng-process-id";

        final byte[] resource = asByteArray(Bpmn.createExecutableProcess(procesId).startEvent().done());

        final WfDefinitionRequestHandler handler = new WfDefinitionRequestHandler(logReader, idGenerator);

        final WfDefinitionRequestReader requestReader = mock(WfDefinitionRequestReader.class);
        when(requestReader.resource()).thenReturn(new UnsafeBuffer(resource));
        when(requestReader.type()).thenReturn(WfDefinitionRequestType.NEW);

        // when
        handler.handle(requestReader, responseControl, logWriters);

        // then
        assertThat(responseControl.size()).isEqualTo(1);

        final ErrorReader errorReader = responseControl.getRejectionValueAs(0, ErrorReader.class);
        assertThat(errorReader.componentCode()).isEqualTo(WfErrors.COMPONENT_CODE);
        assertThat(errorReader.detailCode()).isEqualTo(WfErrors.DEPLOYMENT_ERROR);
        assertThat(errorReader.errorMessage()).isEqualTo("Id of process exceeds max length: 256.");

        assertThat(logWriters.writtenEntries()).isEqualTo(0);
    }

    private static byte[] asByteArray(final BpmnModelInstance bpmnModelInstance)
    {
        final ByteArrayOutputStream modelStream = new ByteArrayOutputStream();
        Bpmn.writeModelToStream(modelStream, bpmnModelInstance);
        final byte[] resource = modelStream.toByteArray();
        return resource;
    }
}
