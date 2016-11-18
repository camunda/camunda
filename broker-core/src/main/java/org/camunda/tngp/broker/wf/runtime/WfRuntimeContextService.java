package org.camunda.tngp.broker.wf.runtime;

import java.util.Arrays;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.log.LogWritersImpl;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.log.handler.ActivityRequestHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.WfDefinitionRequestHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.WorkflowInstanceRequestHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.ActivityEventHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.CreateActivityInstanceHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.EndProcessHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.FlowElementEventHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.ProcessEventHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.StartProcessHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.TakeOutgoingFlowsHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.TriggerNoneEventHandler;
import org.camunda.tngp.broker.wf.runtime.log.handler.bpmn.WaitEventHandler;
import org.camunda.tngp.broker.wf.runtime.log.idx.BpmnEventIndexWriter;
import org.camunda.tngp.broker.wf.runtime.log.idx.WfDefinitionIdIndexWriter;
import org.camunda.tngp.broker.wf.runtime.log.idx.WfDefinitionKeyIndexWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class WfRuntimeContextService implements Service<WfRuntimeContext>
{
    protected final Injector<WfDefinitionCache> wfDefinitionChacheInjector = new Injector<>();
    protected final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    protected final Injector<Log> logInjector = new Injector<>();

    protected final Injector<HashIndexManager<Long2LongHashIndex>> workflowEventIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexInjector = new Injector<>();

    protected final WfRuntimeContext wfRuntimeContext;

    protected Injector<DeferredResponsePool> responsePoolInjector = new Injector<>();

    public WfRuntimeContextService(int id, String name)
    {
        wfRuntimeContext = new WfRuntimeContext(id, name);
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final WfDefinitionCache wfDefinitionCache = wfDefinitionChacheInjector.getValue();
            final IdGenerator idGenerator = idGeneratorInjector.getValue();

            final Log log = logInjector.getValue();
            final HashIndexManager<Long2LongHashIndex> indexManager = workflowEventIndexInjector.getValue();

            final LogWriter logWriter = new LogWriter(log);

            wfRuntimeContext.setLogWriter(logWriter);
            wfRuntimeContext.setLog(log);

            final Templates templates = Templates.wfRuntimeLogTemplates();
            final LogConsumer wfRuntimeConsumer = new LogConsumer(
                    log.getId(),
                    new BufferedLogReader(log),
                    responsePoolInjector.getValue(),
                    templates,
                    new LogWritersImpl(wfRuntimeContext, null));

            final EndProcessHandler endProcessHandler = new EndProcessHandler(new BufferedLogReader(log), indexManager.getIndex());
            final TakeOutgoingFlowsHandler takeOutgoingFlowsHandler = new TakeOutgoingFlowsHandler();
            final WaitEventHandler waitEventHandler = new WaitEventHandler();

            final ActivityEventHandler activityEventHandler = new ActivityEventHandler(wfDefinitionCache, idGenerator);
            activityEventHandler.addAspectHandler(takeOutgoingFlowsHandler);
            activityEventHandler.addAspectHandler(waitEventHandler);
            activityEventHandler.addAspectHandler(endProcessHandler);
            wfRuntimeConsumer.addHandler(Templates.ACTIVITY_EVENT, activityEventHandler);

            final ProcessEventHandler processEventHandler = new ProcessEventHandler(wfDefinitionCache, idGenerator);
            processEventHandler.addAspectHandler(takeOutgoingFlowsHandler);
            processEventHandler.addAspectHandler(waitEventHandler);
            wfRuntimeConsumer.addHandler(Templates.PROCESS_EVENT, processEventHandler);

            final FlowElementEventHandler flowElementEventHandler = new FlowElementEventHandler(wfDefinitionCache, idGenerator);
            flowElementEventHandler.addAspectHandler(new StartProcessHandler());
            flowElementEventHandler.addAspectHandler(new CreateActivityInstanceHandler(new BufferedLogReader(log), indexManager.getIndex()));
            flowElementEventHandler.addAspectHandler(new TriggerNoneEventHandler());
            flowElementEventHandler.addAspectHandler(endProcessHandler);
            wfRuntimeConsumer.addHandler(Templates.FLOW_ELEMENT_EVENT, flowElementEventHandler);

            wfRuntimeConsumer.addHandler(Templates.WF_INSTANCE_REQUEST, new WorkflowInstanceRequestHandler(wfDefinitionCache, idGenerator));
            wfRuntimeConsumer.addHandler(Templates.ACTIVITY_INSTANCE_REQUEST, new ActivityRequestHandler(new BufferedLogReader(log), indexManager.getIndex()));

            wfRuntimeConsumer.addHandler(Templates.WF_DEFINITION_REQUEST,
                    new WfDefinitionRequestHandler(new BufferedLogReader(log), idGenerator));

            wfRuntimeConsumer.addIndexWriter(new BpmnEventIndexWriter(indexManager, templates));
            wfRuntimeConsumer.addIndexWriter(new WfDefinitionIdIndexWriter(wfDefinitionIdIndexInjector.getValue(), Templates.wfRuntimeLogTemplates()));
            wfRuntimeConsumer.addIndexWriter(new WfDefinitionKeyIndexWriter(wfDefinitionKeyIndexInjector.getValue(), Templates.wfRuntimeLogTemplates()));

            wfRuntimeConsumer.recover(Arrays.asList(new BufferedLogReader(log)));


            // replay all events before taking new requests;
            // avoids that we mix up new API requests (that require a response)
            // with existing API requests (that do not require a response anymore)

            // TODO: problem: last position points to the position AFTER the last entry
            //  fast forwarding should be exclusive, or else we read one extra random entry with undefined behavior
            wfRuntimeConsumer.fastForwardToLastEvent();

            wfRuntimeContext.setLogConsumer(wfRuntimeConsumer);
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            wfRuntimeContext.getLogConsumer().writeSavepoints();
        });
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

    public Injector<DeferredResponsePool> getResponsePoolServiceInjector()
    {
        return responsePoolInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getWfDefinitionIdIndexInjector()
    {
        return wfDefinitionIdIndexInjector;
    }

    public Injector<HashIndexManager<Bytes2LongHashIndex>> getWfDefinitionKeyIndexInjector()
    {
        return wfDefinitionKeyIndexInjector;
    }
}
