package org.camunda.tngp.example.msgpack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonExample
{

    public static void main(String[] args) throws JsonProcessingException
    {
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        Map<String, Object> json = new HashMap<>();
        json.put("foo", 1);
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        System.out.println(Arrays.toString(bytes));
        System.out.println(ByteUtil.bytesToBinary(bytes));

    }

}
