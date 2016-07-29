package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCacheService;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.BpmnEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.CreateActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.EndProcessHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.StartProcessHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TakeOutgoingFlowsHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TaskEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TriggerNoneEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.WaitEventHandler;
import org.camunda.tngp.broker.wf.runtime.idx.WorkflowEventIndexWriter;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRuntimeContextService implements Service<WfRuntimeContext>
{
    // TODO: move somewhere else?
    protected static final int READ_BUFFER_SIZE = 1024 * 1024;

    protected final Injector<WfDefinitionCacheService> wfDefinitionChacheInjector = new Injector<>();
    protected final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    protected final Injector<Log> logInjector = new Injector<>();

    protected final Injector<HashIndexManager<Long2LongHashIndex>> workflowEventIndexInjector = new Injector<>();

    protected final WfRuntimeContext wfRuntimeContext;

    public WfRuntimeContextService(int id, String name)
    {
        wfRuntimeContext = new WfRuntimeContext(id, name);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        wfRuntimeContext.setwfDefinitionCacheService(wfDefinitionChacheInjector.getValue());
        wfRuntimeContext.setIdGenerator(idGeneratorInjector.getValue());

        final Log log = logInjector.getValue();
        final LogWriter logWriter = new LogWriter(log);
        final Long2LongHashIndex workflowEventIndex = workflowEventIndexInjector.getValue().getIndex();

        wfRuntimeContext.setLogWriter(logWriter);

        final LogReader logReader = new LogReaderImpl(log, READ_BUFFER_SIZE);
        final BpmnEventHandler bpmnEventHandler = new BpmnEventHandler(wfDefinitionChacheInjector.getValue(), logReader, logWriter, idGeneratorInjector.getValue());

        bpmnEventHandler.addFlowElementHandler(new StartProcessHandler());
        bpmnEventHandler.addFlowElementHandler(new EndProcessHandler(new LogReaderImpl(log, READ_BUFFER_SIZE), workflowEventIndex));
        bpmnEventHandler.addFlowElementHandler(new CreateActivityInstanceHandler());
        bpmnEventHandler.addFlowElementHandler(new TriggerNoneEventHandler());

        final TakeOutgoingFlowsHandler takeOutgoingFlowsHandler = new TakeOutgoingFlowsHandler();
        bpmnEventHandler.addProcessHandler(takeOutgoingFlowsHandler);
        bpmnEventHandler.addActivityHandler(takeOutgoingFlowsHandler);

        final WaitEventHandler waitEventHandler = new WaitEventHandler();
        bpmnEventHandler.addProcessHandler(waitEventHandler);
        bpmnEventHandler.addFlowElementHandler(waitEventHandler);
        bpmnEventHandler.addActivityHandler(waitEventHandler);

        wfRuntimeContext.setBpmnEventHandler(bpmnEventHandler);

        final TaskEventHandler taskEventHandler = new TaskEventHandler(
                new LogReaderImpl(log, READ_BUFFER_SIZE),
                logWriter,
                workflowEventIndexInjector.getValue().getIndex());
        wfRuntimeContext.setTaskEventHandler(taskEventHandler);

        wfRuntimeContext.setActivityInstanceIndexWriter(
                new WorkflowEventIndexWriter(
                    new LogReaderImpl(log, READ_BUFFER_SIZE),
                    workflowEventIndexInjector.getValue().getIndex()));
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public WfRuntimeContext get()
    {
        return wfRuntimeContext;
    }

    public Injector<WfDefinitionCacheService> getwfDefinitionChacheInjector()
    {
        return wfDefinitionChacheInjector;
    }

    public Injector<IdGenerator> getIdGeneratorInjector()
    {
        return idGeneratorInjector;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWorkflowEventIndexInjector()
    {
        return workflowEventIndexInjector;
    }

}
