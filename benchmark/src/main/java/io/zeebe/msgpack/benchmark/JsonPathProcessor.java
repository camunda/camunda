package io.zeebe.msgpack.benchmark;

public interface JsonPathProcessor
{

    String evaluateJsonPath(byte[] json, String jsonPath) throws Exception;
}
