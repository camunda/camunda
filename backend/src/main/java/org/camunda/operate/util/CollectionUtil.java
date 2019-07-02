/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.operate.exceptions.OperateRuntimeException;

public abstract class CollectionUtil {

  @SafeVarargs
  public static <T> List<T> throwAwayNullElements(T... array) {
    List<T> listOfNotNulls = new ArrayList<>();
    for (T o: array) {
      if (o != null) {
        listOfNotNulls.add(o);
      }
    }
    return listOfNotNulls;
  }
  
  @SuppressWarnings("unchecked")
  public static <T> List<T> withoutNulls(Collection<T> aCollection){
    if(aCollection!=null) {
      return aCollection.stream().filter( obj -> obj != null).collect(Collectors.toList());
    }
    return Collections.EMPTY_LIST;
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
  
  public static List<String> toSafeListOfStrings(Collection<?> aCollection){
      return withoutNulls(aCollection).stream().map(obj -> obj.toString()).collect(Collectors.toList());
  }
  
  public static String[] toSafeArrayOfStrings(Collection<?> aCollection){
    return withoutNulls(aCollection).stream().map(obj -> obj.toString()).collect(Collectors.toList()).toArray(new String[]{});
  }
   
  public static List<String> toSafeListOfStrings(Object... objects){
    return toSafeListOfStrings(Arrays.asList(objects));
  }
  
  public static List<Long> toSafeListOfLongs(Collection<String> aCollection){
    return withoutNulls(aCollection).stream().map(obj -> Long.valueOf(obj)).collect(Collectors.toList());
  }
  
  public static List<Long> toSafeListOfLongs(String... strings){
    return toSafeListOfLongs(Arrays.asList(strings));
  }
  
  public static Set<Long> toSafeSetOfLongs(Collection<String> ids) {
    return new HashSet<>(toSafeListOfLongs(ids));
  }

  public static <T> void addNotNull(Collection<T> collection, T object) {
    if (collection!= null && object != null) {
      collection.add(object);
    }
  }
  
  public static Collection<Integer> fromTo(int from,int to){
    Collection<Integer> result = new ArrayList<>();
    for(int i=from;i<=to;i++) {
      result.add(i);
    }
    return result;
  }
}
