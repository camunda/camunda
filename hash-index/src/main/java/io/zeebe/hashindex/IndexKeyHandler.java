package io.zeebe.hashindex;

public interface IndexKeyHandler
{
    void setKeyLength(int keyLength);

    int keyHashCode();

    boolean keyEquals(long keyAddr);

    void readKey(long keyAddr);

    void writeKey(long keyAddr);

    int getKeyLength();

}
