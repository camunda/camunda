package io.zeebe.model.bpmn.instance;

import java.util.Map;

import org.agrona.DirectBuffer;

public interface TaskHeaders
{

    DirectBuffer asMsgpackEncoded();

    Map<String, String> asMap();

}
