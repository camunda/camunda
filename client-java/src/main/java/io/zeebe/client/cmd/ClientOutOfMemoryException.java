package io.zeebe.client.cmd;

public class ClientOutOfMemoryException extends ClientException
{
    private static final long serialVersionUID = 1L;

    public ClientOutOfMemoryException(String message)
    {
        super(message);
    }
}
