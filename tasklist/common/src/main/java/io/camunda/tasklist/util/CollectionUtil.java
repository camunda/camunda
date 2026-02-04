/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ConversionUtils.STRING_TO_LONG;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.FieldValue;

public abstract class CollectionUtil {

  public static <K, V> V getOrDefaultForNullValue(
      final Map<K, V> map, final K key, final V defaultValue) {
    final V value = map.get(key);
    return value == null ? defaultValue : value;
  }

  @SafeVarargs
  public static <T> List<T> throwAwayNullElements(final T... array) {
    final List<T> listOfNotNulls = new ArrayList<>();
    for (final T o : array) {
      if (o != null) {
        listOfNotNulls.add(o);
      }
    }
    return listOfNotNulls;
  }

  public static <T> List<T> emptyListWhenNull(final List<T> aList) {
    return aList == null ? Collections.emptyList() : aList;
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> withoutNulls(final Collection<T> aCollection) {
    if (aCollection != null) {
      return filter(aCollection, obj -> obj != null);
    }
    return Collections.EMPTY_LIST;
  }

  public static <T, S> Map<T, List<S>> addToMap(
      final Map<T, List<S>> map, final T key, final S value) {
    map.computeIfAbsent(key, k -> new ArrayList<S>()).add(value);
    return map;
  }

  public static Map<String, Object> asMap(final Object... keyValuePairs) {
    if (keyValuePairs == null || keyValuePairs.length % 2 != 0) {
      throw new TasklistRuntimeException("keyValuePairs should not be null and has a even length.");
    }
    final Map<String, Object> result = new HashMap<String, Object>();
    for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
      result.put(keyValuePairs[i].toString(), keyValuePairs[i + 1]);
    }
    return result;
  }

  // We need this because map::getOrDefault throws an exception for null keys :-(
  public static <K, T> T getOrDefaultFromMap(
      final Map<K, T> map, final K key, final T defaultValue) {
    return key == null ? defaultValue : map.getOrDefault(key, defaultValue);
  }

  public static <T> T firstOrDefault(final List<T> list, final T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  public static <S, T> List<T> map(final Collection<S> sourceList, final Function<S, T> mapper) {
    return map(sourceList.stream(), mapper);
  }

  public static <S, T> List<T> map(final S[] sourceArray, final Function<S, T> mapper) {
    return map(Arrays.stream(sourceArray).parallel(), mapper);
  }

  public static <S, T> List<T> map(final Stream<S> sequenceStream, final Function<S, T> mapper) {
    return sequenceStream.map(mapper).collect(Collectors.toList());
  }

  public static <T> List<T> filter(final Collection<T> collection, final Predicate<T> predicate) {
    return filter(collection.stream(), predicate);
  }

  public static <T> List<T> filter(final Stream<T> filterStream, final Predicate<T> predicate) {
    return filterStream.filter(predicate).collect(Collectors.toList());
  }

  public static List<String> toSafeListOfStrings(final Collection<?> aCollection) {
    return map(withoutNulls(aCollection), obj -> obj.toString());
  }

  public static String[] toSafeArrayOfStrings(final Collection<?> aCollection) {
    return toSafeListOfStrings(aCollection).toArray(new String[] {});
  }

  public static List<String> toSafeListOfStrings(final Object... objects) {
    return toSafeListOfStrings(Arrays.asList(objects));
  }

  public static List<Long> toSafeListOfLongs(final Collection<String> aCollection) {
    return map(withoutNulls(aCollection), STRING_TO_LONG);
  }

  public static List<FieldValue> toSafeListOfOSFieldValues(final Object... objects) {
    return toSafeListOfStrings(Arrays.asList(objects)).stream().map(FieldValue::of).toList();
  }

  public static <T> void addNotNull(final Collection<T> collection, final T object) {
    if (collection != null && object != null) {
      collection.add(object);
    }
  }

  public static List<Integer> fromTo(final int from, final int to) {
    final List<Integer> result = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      result.add(i);
    }
    return result;
  }

  public static boolean isNotEmpty(final Collection<?> aCollection) {
    return aCollection != null && !aCollection.isEmpty();
  }

  /**
   * @param subsetId starts from 0
   */
  public static <E> List<E> splitAndGetSublist(
      final List<E> list, final int subsetCount, final int subsetId) {
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

  public static <T> T chooseOne(final List<T> items) {
    return items.get(new Random().nextInt(items.size()));
  }

  public static <T> boolean allElementsAreOfType(final Class clazz, final T... array) {
    for (final T element : array) {
      if (!clazz.isInstance(element)) {
        return false;
      }
    }
    return true;
  }

  public static String[] toArrayOfStrings(final Object[] items) {
    return Arrays.stream(items).map(String::valueOf).toArray(String[]::new);
  }

  public static long countNonNullObjects(final Object... objects) {
    return Arrays.stream(objects).filter(Objects::nonNull).count();
  }
}
