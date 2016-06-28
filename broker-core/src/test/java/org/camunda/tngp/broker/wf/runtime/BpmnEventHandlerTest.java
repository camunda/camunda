package org.camunda.tngp.broker.wf.runtime;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.tngp.bpmn.graph.FlowElementVisitor;
import org.camunda.tngp.bpmn.graph.ProcessGraph;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.graph.bpmn.BpmnAspect;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BpmnEventHandlerTest
{

    protected BpmnEventHandler eventHandler;

    @Mock
    protected WfTypeCacheService processCache;

    @Mock
    protected ProcessGraph process;

    @Mock
    protected LogReader logReader;

    @Mock
    protected LogWriter logWriter;

    @Mock
    protected BpmnFlowElementEventHandler handler;

    @Mock
    protected BpmnEventReader eventReader;

    @Mock
    protected BpmnFlowElementEventReader flowElementEventReader;

    protected IdGenerator idGenerator;

    protected FlowElementVisitor flowElementVisitor;


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        flowElementVisitor = mock(FlowElementVisitor.class, new FluentAnswer());
        idGenerator = new PrivateIdGenerator(0);

        eventHandler = new BpmnEventHandler(processCache, logReader, logWriter, idGenerator);
        eventHandler.setEventReader(eventReader);
        eventHandler.setFlowElementVisitor(flowElementVisitor);

        when(processCache.getProcessGraphByTypeId(1234L)).thenReturn(process);
    }

    @Test
    public void testHandleFlowElementEvent()
    {
        // given
        when(handler.getHandledBpmnAspect()).thenReturn(BpmnAspect.START_PROCESS);
        eventHandler.addFlowElementHandler(handler);

        when(logReader.read(any())).thenReturn(true, false);

        when(eventReader.templateId()).thenReturn(BpmnFlowElementEventDecoder.TEMPLATE_ID);
        when(eventReader.flowElementEvent()).thenReturn(flowElementEventReader);

        when(flowElementEventReader.event()).thenReturn(ExecutionEventType.EVT_OCCURRED);
        when(flowElementVisitor.aspectFor(ExecutionEventType.EVT_OCCURRED)).thenReturn(BpmnAspect.START_PROCESS);
        when(flowElementEventReader.processId()).thenReturn(1234L);

        // when
        eventHandler.doWork();

        // then
        final InOrder inOrder = Mockito.inOrder(logReader, handler);

        inOrder.verify(logReader).read(eventReader);
        inOrder.verify(handler).handle(flowElementEventReader, process, logWriter, idGenerator);
    }

}
