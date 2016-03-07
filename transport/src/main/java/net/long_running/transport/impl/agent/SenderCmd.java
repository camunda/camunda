package net.long_running.transport.impl.agent;

@FunctionalInterface
public interface SenderCmd
{
    void execute(Sender sender);
}
