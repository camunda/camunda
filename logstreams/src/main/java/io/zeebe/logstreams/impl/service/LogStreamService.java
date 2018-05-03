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
package io.zeebe.logstreams.impl.service;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.*;
import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static io.zeebe.logstreams.log.LogStreamUtil.getAddressForPosition;

import io.zeebe.dispatcher.*;
import io.zeebe.logstreams.impl.*;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.status.Position;

public class LogStreamService implements LogStream, Service<LogStream>
{
    private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

    private final Injector<LogStorage> logStorageInjector = new Injector<>();
    private final Injector<LogBlockIndex> logBlockIndexInjector = new Injector<>();
    private final Injector<LogBlockIndexWriter> logBockIndexWriterInjector = new Injector<>();

    private final ServiceContainer serviceContainer;

    private final ActorConditions onLogStorageAppendedConditions = new ActorConditions();
    private final ActorConditions onCommitPositionUpdatedConditions;

    private final String logName;
    private final DirectBuffer topicName;
    private final int partitionId;

    private final ByteValue writeBufferSize;
    private final int maxAppendBlockSize;

    private final Position commitPosition;
    private volatile int term = 0;

    private ServiceStartContext serviceContext;

    private LogStorage logStorage;
    private LogBlockIndex logBlockIndex;
    private LogBlockIndexWriter logBlockIndexWriter;

    private ActorFuture<Dispatcher> writeBufferFuture;
    private ActorFuture<LogStorageAppender> appenderFuture;
    private Dispatcher writeBuffer;
    private LogStorageAppender appender;


    public LogStreamService(LogStreamBuilder builder)
    {
        this.logName = builder.getLogName();
        this.topicName = builder.getTopicName();
        this.partitionId = builder.getPartitionId();
        this.serviceContainer = builder.getServiceContainer();
        this.onCommitPositionUpdatedConditions = builder.getOnCommitPositionUpdatedConditions();
        this.commitPosition = builder.getCommitPosition();
        this.writeBufferSize = ByteValue.ofBytes(builder.getWriteBufferSize());
        this.maxAppendBlockSize = builder.getMaxAppendBlockSize();
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        commitPosition.setVolatile(INVALID_ADDRESS);

        serviceContext = startContext;
        logStorage = logStorageInjector.getValue();
        logBlockIndex = logBlockIndexInjector.getValue();
        logBlockIndexWriter = logBockIndexWriterInjector.getValue();
    }

    @Override
    public ActorFuture<LogStorageAppender> openAppender()
    {
        final ServiceName<Void> logStorageAppenderRootService = logStorageAppenderRootService(logName);
        final ServiceName<Dispatcher> logWriteBufferServiceName = logWriteBufferServiceName(logName);
        final ServiceName<Subscription> appenderSubscriptionServiceName = logWriteBufferSubscriptionServiceName(logName, APPENDER_SUBSCRIPTION_NAME);
        final ServiceName<LogStorageAppender> logStorageAppenderServiceName = logStorageAppenderServiceName(logName);

        final DispatcherBuilder writeBufferBuilder = Dispatchers.create(logWriteBufferServiceName.getName())
            .bufferSize(writeBufferSize);

        final CompositeServiceBuilder installOperation = serviceContext.createComposite(logStorageAppenderRootService);

        final LogWriteBufferService writeBufferService = new LogWriteBufferService(writeBufferBuilder);
        writeBufferFuture = installOperation.createService(logWriteBufferServiceName, writeBufferService)
            .dependency(logStorageInjector.getInjectedServiceName(), writeBufferService.getLogStorageInjector())
            .dependency(logBlockIndexInjector.getInjectedServiceName(), writeBufferService.getLogBlockIndexInjector())
            .install();

        final LogWriteBufferSubscriptionService subscriptionService = new LogWriteBufferSubscriptionService(APPENDER_SUBSCRIPTION_NAME);
        installOperation.createService(appenderSubscriptionServiceName, subscriptionService)
            .dependency(logWriteBufferServiceName, subscriptionService.getWritebufferInjector())
            .install();

        final LogStorageAppenderService appenderService = new LogStorageAppenderService(onLogStorageAppendedConditions, maxAppendBlockSize);
        appenderFuture = installOperation.createService(logStorageAppenderServiceName, appenderService)
            .dependency(appenderSubscriptionServiceName, appenderService.getAppenderSubscriptionInjector())
            .dependency(logStorageInjector.getInjectedServiceName(), appenderService.getLogStorageInjector())
            .install();

        return installOperation.installAndReturn(logStorageAppenderServiceName);
    }

    @Override
    public ActorFuture<Void> closeAppender()
    {
        appenderFuture = null;
        writeBufferFuture = null;
        appender = null;
        writeBuffer = null;

        return serviceContext.removeService(logStorageAppenderRootService(logName));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public LogStream get()
    {
        return this;
    }

    @Override
    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public String getLogName()
    {
        return logName;
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    @Override
    public ActorFuture<Void> closeAsync()
    {
        return serviceContainer.removeService(logStreamRootServiceName(logName));
    }

    @Override
    public LogStorage getLogStorage()
    {
        return logStorage;
    }

    @Override
    public LogBlockIndex getLogBlockIndex()
    {
        return logBlockIndex;
    }

    @Override
    public LogBlockIndexWriter getLogBlockIndexWriter()
    {
        return logBlockIndexWriter;
    }

    @Override
    public Dispatcher getWriteBuffer()
    {
        if (writeBuffer == null && writeBufferFuture != null)
        {
            writeBuffer = writeBufferFuture.join();
        }
        return writeBuffer;
    }

    @Override
    public LogStorageAppender getLogStorageAppender()
    {
        if (appender == null && appenderFuture != null)
        {
            appender = appenderFuture.join();
        }
        return appender;
    }

    @Override
    public long getCommitPosition()
    {
        return commitPosition.get();
    }

    @Override
    public void truncate(long position)
    {
        if (position <= getCommitPosition())
        {
            throw new IllegalArgumentException("Can't truncate position which is already committed");
        }

        final long truncateAddress = getAddressForPosition(this, position);
        if (truncateAddress != INVALID_ADDRESS)
        {
            logStorage.truncate(truncateAddress);
        }
        else
        {
            throw new IllegalArgumentException(String.format("Truncation failed! Position %d was not found.", position));
        }
    }

    @Override
    public void setCommitPosition(long commitPosition)
    {
        this.commitPosition.setOrdered(commitPosition);

        onCommitPositionUpdatedConditions.signalConsumers();
    }

    @Override
    public void registerOnCommitPositionUpdatedCondition(ActorCondition condition)
    {
        onCommitPositionUpdatedConditions.registerConsumer(condition);
    }

    @Override
    public void removeOnCommitPositionUpdatedCondition(ActorCondition condition)
    {
        onCommitPositionUpdatedConditions.removeConsumer(condition);
    }

    @Override
    public void registerOnAppendCondition(ActorCondition condition)
    {
        onLogStorageAppendedConditions.registerConsumer(condition);
    }

    @Override
    public void removeOnAppendCondition(ActorCondition condition)
    {
        onLogStorageAppendedConditions.removeConsumer(condition);
    }

    @Override
    public int getTerm()
    {
        return term;
    }

    @Override
    public void setTerm(int term)
    {
        this.term = term;
    }

    public Injector<LogBlockIndex> getLogBlockIndexInjector()
    {
        return logBlockIndexInjector;
    }

    public Injector<LogBlockIndexWriter> getLogBockIndexWriterInjector()
    {
        return logBockIndexWriterInjector;
    }

    public Injector<LogStorage> getLogStorageInjector()
    {
        return logStorageInjector;
    }
}
