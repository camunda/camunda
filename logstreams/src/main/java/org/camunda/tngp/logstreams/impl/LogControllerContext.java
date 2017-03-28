package org.camunda.tngp.logstreams.impl;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.ReadResultProcessor;
import org.camunda.tngp.util.agent.AgentRunnerService;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Represents the context for the log controller's.
 * Encapsulate the mandatory properties for the log controller's and
 * offers some convenient methods to act on these properties.
 */
public class LogControllerContext
{
    protected final String name;
    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;
    protected final AgentRunnerService agentRunnerService;

    public LogControllerContext(String name, LogStorage logStorage,
                                LogBlockIndex blockIndex, AgentRunnerService agentRunnerService)
    {
        Objects.requireNonNull(name, "name");
        this.name = name;

        Objects.requireNonNull(logStorage, "logStorage");
        this.logStorage = logStorage;

        Objects.requireNonNull(blockIndex, "blockIndex");
        this.blockIndex = blockIndex;

        Objects.requireNonNull(agentRunnerService, "agentRunnerService");
        this.agentRunnerService = agentRunnerService;
    }

    public String getName()
    {
        return name;
    }

    // LOG STORAGE /////////////////////
    public LogStorage getLogStorage()
    {
        return logStorage;
    }

    public long logStorageAppend(ByteBuffer buffer)
    {
        return logStorage.append(buffer);
    }

    public boolean isLogStorageOpen()
    {
        return logStorage.isOpen();
    }

    public void logStorageOpen()
    {
        if (!isLogStorageOpen())
        {
            logStorage.open();
        }
    }

    public long logStorageFirstBlockAddress()
    {
        return logStorage.getFirstBlockAddress();
    }

    public long logStorageRead(ByteBuffer buffer, long address)
    {
        return logStorage.read(buffer, address);
    }

    public long logStorageRead(ByteBuffer buffer, long address, ReadResultProcessor processor)
    {
        return logStorage.read(buffer, address, processor);
    }

    public void logStorageFlush() throws Exception
    {
        logStorage.flush();
    }

    public void logStorageClose()
    {
        logStorage.close();
    }

    // LOG BLOCK INDEX /////////////////////

    public LogBlockIndex getBlockIndex()
    {
        return blockIndex;
    }

    public long logBlockIndexLookupBlockAddress(long position)
    {
        return blockIndex.lookupBlockAddress(position);
    }

    public void logBlockIndexAddBlock(long position, long address)
    {
        blockIndex.addBlock(position, address);
    }

    public long logBlockIndexTryLookupBlockAddress(long position)
    {
        return blockIndex.size() > 0
            ? blockIndex.lookupBlockAddress(position)
            : logStorage.getFirstBlockAddress();
    }

    // returns -1 if no index exist
    public int logBlockIndexLastIndex()
    {
        return blockIndex.size() - 1;
    }

    public long logBlockIndexLogPosition(int index)
    {
        return blockIndex.getLogPosition(index);
    }

    public void truncateLogBlockIndexAndLogStorage(long position, long address)
    {
        blockIndex.truncate(position);
        logStorage.truncate(address);
    }

    // AGENT RUNNER SERVICE //////////////////

    public AgentRunnerService getAgentRunnerService()
    {
        return agentRunnerService;
    }

    public void agentRunnerServiceRun(Agent agent)
    {
        agentRunnerService.run(agent);
    }

    public void agentRunnerServiceRemove(Agent agent)
    {
        agentRunnerService.remove(agent);
    }
}
