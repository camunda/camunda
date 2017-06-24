package io.zeebe.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerSocketBinding
{
    protected final List<Selector> registeredSelectors = Collections.synchronizedList(new ArrayList<>());
    protected final InetSocketAddress bindAddress;

    protected ServerSocketChannel media;

    public ServerSocketBinding(
            final InetSocketAddress bindAddress)
    {
        this.bindAddress = bindAddress;
    }

    public void doBind()
    {
        try
        {
            media = ServerSocketChannel.open();
            media.bind(bindAddress);
            media.configureBlocking(false);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void registerSelector(Selector selector, int op)
    {
        try
        {
            final SelectionKey key = media.register(selector, op);
            key.attach(this);
            registeredSelectors.add(selector);
        }
        catch (ClosedChannelException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void removeSelector(Selector selector)
    {
        final SelectionKey key = media.keyFor(selector);
        if (key != null)
        {
            key.cancel();

            try
            {
                // required to reuse socket on windows, see https://github.com/kaazing/nuklei/issues/20
                selector.select(1);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public SocketChannel accept()
    {
        try
        {
            final SocketChannel socketChannel = media.accept();
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.configureBlocking(false);
            return socketChannel;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void close()
    {
        try
        {
            synchronized (registeredSelectors)
            {
                registeredSelectors.forEach(s -> removeSelector(s));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        try
        {
            media.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
