package org.camunda.tngp.broker.wf.executor;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.BpmnExecutor;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class BpmnExecutorService implements Service<BpmnExecutor>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    protected final Injector<HashIndexManager<Long2LongHashIndex>> keyIndexInjector = new Injector<>();

    protected BpmnExecutor bpmnExecutor;

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Log log = logInjector.getValue();
        final IdGenerator idGenerator = idGeneratorInjector.getValue();
        final Long2LongHashIndex keyIndex = keyIndexInjector.getValue().getIndex();

        final BrokerExecutionLog brokerExecutionLog = new BrokerExecutionLog(log, keyIndex);
        final BrokerBpmnIdGenerator brokerBpmnIdGenerator = new BrokerBpmnIdGenerator(idGenerator);

        final WfRuntimeContext executorContex = new WfRuntimeContext();
        executorContex.setBpmnIdGenerator(brokerBpmnIdGenerator);
        executorContex.setExecutionLog(brokerExecutionLog);

        bpmnExecutor = new BpmnExecutor(executorContex);
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public BpmnExecutor get() {
        return bpmnExecutor;
    }

    public Injector<IdGenerator> getIdGeneratorInjector() {
        return idGeneratorInjector;
    }

    public Injector<HashIndexManager<Long2LongHashIndex>> getKeyIndexInjector() {
        return keyIndexInjector;
    }

    public Injector<Log> getLogInjector() {
        return logInjector;
    }

}
