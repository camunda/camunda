package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfDefinitionCache;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.BpmnEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.CreateActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.EndProcessHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.StartProcessHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TakeOutgoingFlowsHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TaskEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.TriggerNoneEventHandler;
import org.camunda.tngp.broker.wf.runtime.bpmn.handler.WaitEventHandler;
import org.camunda.tngp.broker.wf.runtime.idx.WorkflowEventIndexLogTracker;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfRuntimeContextService implements Service<WfRuntimeContext>
{
    protected final Injector<WfDefinitionCache> wfDefinitionChacheInjector = new Injector<>();
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
        wfRuntimeContext.setWfDefinitionCache(wfDefinitionChacheInjector.getValue());
        wfRuntimeContext.setIdGenerator(idGeneratorInjector.getValue());

        final Log log = logInjector.getValue();
        final HashIndexManager<Long2LongHashIndex> indexManager = workflowEventIndexInjector.getValue();
        final WorkflowEventIndexLogTracker indexLogTracker = new WorkflowEventIndexLogTracker(indexManager.getIndex());
        final IndexWriter<BpmnEventReader> indexWriter = new IndexWriter<>(
                new LogReaderImpl(log),
                log.getWriteBuffer().openSubscription(),
                log.getId(),
                new BpmnEventReader(),
                indexLogTracker,
                new HashIndexManager<?>[]{indexManager});

        final LogWriter logWriter = new LogWriter(log, indexWriter);

        wfRuntimeContext.setLogWriter(logWriter);

        final BpmnEventHandler bpmnEventHandler = new BpmnEventHandler(wfDefinitionChacheInjector.getValue(), new LogReaderImpl(log), logWriter, idGeneratorInjector.getValue());

        bpmnEventHandler.addFlowElementHandler(new StartProcessHandler());
        bpmnEventHandler.addFlowElementHandler(new CreateActivityInstanceHandler());
        bpmnEventHandler.addFlowElementHandler(new TriggerNoneEventHandler());

        final EndProcessHandler endProcessHandler = new EndProcessHandler(new LogReaderImpl(log), indexManager.getIndex());
        bpmnEventHandler.addFlowElementHandler(endProcessHandler);
        bpmnEventHandler.addActivityHandler(endProcessHandler);

        final TakeOutgoingFlowsHandler takeOutgoingFlowsHandler = new TakeOutgoingFlowsHandler();
        bpmnEventHandler.addProcessHandler(takeOutgoingFlowsHandler);
        bpmnEventHandler.addActivityHandler(takeOutgoingFlowsHandler);

        final WaitEventHandler waitEventHandler = new WaitEventHandler();
        bpmnEventHandler.addProcessHandler(waitEventHandler);
        bpmnEventHandler.addFlowElementHandler(waitEventHandler);
        bpmnEventHandler.addActivityHandler(waitEventHandler);

        wfRuntimeContext.setBpmnEventHandler(bpmnEventHandler);

        final TaskEventHandler taskEventHandler = new TaskEventHandler(
                new LogReaderImpl(log),
                logWriter,
                workflowEventIndexInjector.getValue().getIndex());
        wfRuntimeContext.setTaskEventHandler(taskEventHandler);

        wfRuntimeContext.setIndexWriter(indexWriter);
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

    public Injector<WfDefinitionCache> getWfDefinitionChacheInjector()
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
