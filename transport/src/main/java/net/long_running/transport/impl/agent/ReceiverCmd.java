package net.long_running.transport.impl.agent;

@FunctionalInterface
public interface ReceiverCmd
{
    void execute(Receiver receiver);
}
