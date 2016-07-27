package org.camunda.tngp.broker.wf.runtime.bpmn.handler;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.test.util.BufferWriterMatcher;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnFlowElementEventWriter;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BpmnEventHandlerTest
{

    protected BpmnEventHandler eventHandler;

    @Mock
    protected WfDefinitionCache processCache;

    @Mock
    protected ProcessGraph process;

    @Mock
    protected LogWriter logWriter;

    protected FlowElementVisitor flowElementVisitor;

    protected StubLogReader logReader;


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        flowElementVisitor = mock(FlowElementVisitor.class, new FluentAnswer());

        logReader = new StubLogReader(null);

        eventHandler = new BpmnEventHandler(processCache, logReader, logWriter, new PrivateIdGenerator(0));
        eventHandler.setFlowElementVisitor(flowElementVisitor);

        when(processCache.getProcessGraphByTypeId(1234L)).thenReturn(process);
    }

    @Test
    public void testHandleFlowElementEvent()
    {
        // given
        logReader.addEntry(
                new BpmnFlowElementEventWriter()
                    .eventType(ExecutionEventType.EVT_OCCURRED)
                    .flowElementId(123)
                    .processId(1234L));

        eventHandler.addFlowElementHandler(new StartProcessHandler());

        when(flowElementVisitor.aspectFor(ExecutionEventType.EVT_OCCURRED)).thenReturn(BpmnAspect.START_PROCESS);

        // when
        eventHandler.doWork();

        // then
        verify(logWriter).write(
                argThat(BufferWriterMatcher
                    .writesProperties(BpmnProcessEventReader.class)
                    .matching((e) -> e.event(), ExecutionEventType.PROC_INST_CREATED)));
    }

}
