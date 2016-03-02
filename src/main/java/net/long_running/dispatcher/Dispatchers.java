package net.long_running.dispatcher;

public class Dispatchers
{
    public static DispatcherBuilder create(String name)
    {
        return new DispatcherBuilder(name);
    }

}
