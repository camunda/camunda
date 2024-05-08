/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ConversionUtils.STRING_TO_LONG;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class CollectionUtil {

  public static <K, V> V getOrDefaultForNullValue(Map<K, V> map, K key, V defaultValue) {
    final V value = map.get(key);
    return value == null ? defaultValue : value;
  }

  public static <K, T> T getOrDefaultFromMap(Map<K, T> map, K key, T defaultValue) {
    return key == null ? defaultValue : map.getOrDefault(key, defaultValue);
  }

  public static <T> T firstOrDefault(List<T> list, T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  @SafeVarargs
  public static <T> List<T> throwAwayNullElements(T... array) {
    final List<T> listOfNotNulls = new ArrayList<>();
    for (T o : array) {
      if (o != null) {
        listOfNotNulls.add(o);
      }
    }
    return listOfNotNulls;
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    final Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  public static <T> List<T> emptyListWhenNull(List<T> aList) {
    return aList == null ? Collections.emptyList() : aList;
  }

  public static <T> List<T> withoutNulls(Collection<T> aCollection) {
    if (aCollection != null) {
      return filter(aCollection, Objects::nonNull);
    }
    return Collections.emptyList();
  }

  public static <T, S> Map<T, List<S>> addToMap(Map<T, List<S>> map, T key, S value) {
    map.computeIfAbsent(key, k -> new ArrayList<S>()).add(value);
    return map;
  }

  public static Map<String, Object> asMap(Object... keyValuePairs) {
    if (keyValuePairs == null || keyValuePairs.length % 2 != 0) {
      throw new OperateRuntimeException("keyValuePairs should not be null and has a even length.");
    }
    final Map<String, Object> result = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
      result.put(keyValuePairs[i].toString(), keyValuePairs[i + 1]);
    }
    return result;
  }

  public static <S, T> List<T> map(Collection<S> sourceList, Function<? super S, T> mapper) {
    return map(sourceList.stream(), mapper);
  }

  public static <S, T> List<T> map(S[] sourceArray, Function<S, T> mapper) {
    return map(Arrays.stream(sourceArray).parallel(), mapper);
  }

  public static <S, T> List<T> map(Stream<S> sequenceStream, Function<? super S, T> mapper) {
    return sequenceStream.map(mapper).collect(Collectors.toList());
  }

  public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
    return filter(collection.stream(), predicate);
  }

  public static <T> List<T> filter(Stream<T> filterStream, Predicate<T> predicate) {
    return filterStream.filter(predicate).collect(Collectors.toList());
  }

  public static List<String> toSafeListOfStrings(Collection<?> aCollection) {
    return map(withoutNulls(aCollection), Object::toString);
  }

  public static String[] toSafeArrayOfStrings(Collection<?> aCollection) {
    return toSafeListOfStrings(aCollection).toArray(new String[] {});
  }

  public static List<String> toSafeListOfStrings(Object... objects) {
    return toSafeListOfStrings(Arrays.asList(objects));
  }

  public static List<Long> toSafeListOfLongs(Collection<String> aCollection) {
    return map(withoutNulls(aCollection), STRING_TO_LONG);
  }

  public static <T> void addNotNull(Collection<T> collection, T object) {
    if (collection != null && object != null) {
      collection.add(object);
    }
  }

  public static List<Integer> fromTo(int from, int to) {
    final List<Integer> result = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      result.add(i);
    }
    return result;
  }

  public static boolean isNotEmpty(Collection<?> aCollection) {
    return aCollection != null && !aCollection.isEmpty();
  }

  public static boolean isEmpty(Collection<?> aCollection) {
    return aCollection == null || aCollection.isEmpty();
  }

  public static boolean isEmpty(Object... objects) {
    return objects == null || objects.length == 0;
  }

  /**
   * @param list
   * @param subsetCount
   * @param subsetId starts from 0
   * @param <E>
   * @return
   */
  public static <E> List<E> splitAndGetSublist(List<E> list, int subsetCount, int subsetId) {
    if (subsetId >= subsetCount) {
      return new ArrayList<>();
    }
    final Integer size = list.size();
    final int bucketSize = (int) Math.round((double) size / (double) subsetCount);
    final int start = bucketSize * subsetId;
    final int end;
    if (subsetId == subsetCount - 1) {
      end = size;
    } else {
      end = start + bucketSize;
    }
    return new ArrayList<>(list.subList(start, end));
  }

  public static <T> T chooseOne(List<T> items) {
    return items.get(new Random().nextInt(items.size()));
  }

  public static <T> boolean allElementsAreOfType(Class clazz, T... array) {
    for (T element : array) {
      if (!clazz.isInstance(element)) {
        return false;
      }
    }
    return true;
  }

  public static long countNonNullObjects(Object... objects) {
    return Arrays.stream(objects).filter(Objects::nonNull).count();
  }

  public static <T> List<T> reversedView(final List<T> list) {
    return new AbstractList<T>() {
      @Override
      public T get(int index) {
        return list.get(list.size() - 1 - index);
      }

      @Override
      public int size() {
        return list.size();
      }
    };
  }
}
