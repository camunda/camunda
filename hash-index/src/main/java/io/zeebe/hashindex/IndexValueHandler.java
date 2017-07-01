package io.zeebe.hashindex;

public interface IndexValueHandler
{
    int getValueLength();

    void writeValue(long writeValueAddr);

    void readValue(long valueAddr, int valueLength);
}