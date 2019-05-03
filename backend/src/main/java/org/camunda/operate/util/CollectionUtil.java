/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.operate.exceptions.OperateRuntimeException;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class CollectionUtil {

  public static <T> List<T> throwAwayNullElements(T... array) {
    List<T> listOfNotNulls = new ArrayList<>();
    for (T o: array) {
      if (o != null) {
        listOfNotNulls.add(o);
      }
    }
    return listOfNotNulls;
  }

  public static <T, S> Map<T,List<S>> addToMap(Map<T, List<S>> map, T key, S value) {
    map.computeIfAbsent(key, k -> new ArrayList<S>()).add(value);
    return map;
  }
 
  public static Map<String, Object> asMap(Object ...keyValuePairs){
    if(keyValuePairs == null || keyValuePairs.length % 2 != 0) {
      throw new OperateRuntimeException("keyValuePairs should not be null and has a even length.");
    }
    Map<String,Object> result = new HashMap<String, Object>();
    for(int i=0;i<keyValuePairs.length-1;i+=2) {
      result.put(keyValuePairs[i].toString(), keyValuePairs[i+1]);
    }
    return result;
  }

  public static <T> void addNotNull(Collection<T> collection, T object) {
    if (collection!= null && object != null) {
      collection.add(object);
    }
  }

  public static <T> Set<T> asSet(T ...objects){
    Set<T> result = new HashSet<>();
    for(T object: objects) {
      if(object!=null) result.add(object);
    }
    return result;
  }
  
  public static Map<String, Object> toJSONMap(ObjectMapper objectMapper,Object input) throws IOException {
    //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
    return objectMapper.readValue(objectMapper.writeValueAsString(input), HashMap.class);
  }
}
