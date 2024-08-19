/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto.toExternalProcessVariableDtos;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import io.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import io.camunda.optimize.service.events.ExternalEventService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.VariableHelper;
import io.camunda.optimize.service.variable.ExternalVariableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path(INGESTION_PATH)
@Component
public class IngestionRestService {

  public static final String INGESTION_PATH = "/ingestion";
  public static final String EVENT_BATCH_SUB_PATH = "/event/batch";
  public static final String VARIABLE_SUB_PATH = "/variable";
  public static final String CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH =
      "application/cloudevents-batch+json";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(IngestionRestService.class);

  private final ExternalEventService externalEventService;
  private final ExternalVariableService externalVariableService;

  public IngestionRestService(
      final ExternalEventService externalEventService,
      final ExternalVariableService externalVariableService) {
    this.externalEventService = externalEventService;
    this.externalVariableService = externalVariableService;
  }

  @POST
  @Path(EVENT_BATCH_SUB_PATH)
  @Consumes({CONTENT_TYPE_CLOUD_EVENTS_V1_JSON_BATCH, MediaType.APPLICATION_JSON})
  @Produces(MediaType.APPLICATION_JSON)
  public void ingestCloudEvents(
      final @Context ContainerRequestContext requestContext,
      final @NotNull @Valid @RequestBody ValidList<CloudEventRequestDto> cloudEventDtos) {
    externalEventService.saveEventBatch(mapToEventDto(cloudEventDtos));
  }

  @POST
  @Path(VARIABLE_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  public void ingestVariables(
      final @Context ContainerRequestContext requestContext,
      final @NotNull @Valid @RequestBody List<ExternalProcessVariableRequestDto> variableDtos) {
    validateVariableType(variableDtos);
    externalVariableService.storeExternalProcessVariables(
        toExternalProcessVariableDtos(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(), variableDtos));
  }

  private void validateVariableType(final List<ExternalProcessVariableRequestDto> variables) {
    if (variables.stream()
        .anyMatch(variable -> !VariableHelper.isProcessVariableTypeSupported(variable.getType()))) {
      throw new BadRequestException(
          String.format(
              "A given variable type is not supported. The type must always be one of: %s",
              ReportConstants.ALL_SUPPORTED_PROCESS_VARIABLE_TYPES));
    }
  }

  private static List<EventDto> mapToEventDto(final List<CloudEventRequestDto> cloudEventDtos) {
    final Instant rightNow = LocalDateUtil.getCurrentDateTime().toInstant();
    return cloudEventDtos.stream()
        .map(
            cloudEventDto ->
                EventDto.builder()
                    .id(cloudEventDto.getId())
                    .eventName(cloudEventDto.getType())
                    .timestamp(
                        cloudEventDto
                            .getTime()
                            .orElse(rightNow) // In case no time was passed as a parameter, use the
                            // current time instead
                            .toEpochMilli())
                    .traceId(cloudEventDto.getTraceid())
                    .group(cloudEventDto.getGroup().orElse(null))
                    .source(cloudEventDto.getSource())
                    .data(cloudEventDto.getData())
                    .build())
        .collect(toList());
  }

  private static class ValidList<E> implements List<E> {

    private List<E> list = new ArrayList<>();

    public ValidList() {}

    public List<E> getList() {
      return list;
    }

    public void setList(final List<E> list) {
      this.list = list;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ValidList;
    }

    @Override
    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $list = getList();
      result = result * PRIME + ($list == null ? 43 : $list.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ValidList)) {
        return false;
      }
      final ValidList<?> other = (ValidList<?>) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$list = getList();
      final Object other$list = other.getList();
      if (this$list == null ? other$list != null : !this$list.equals(other$list)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "IngestionRestService.ValidList(list=" + getList() + ")";
    }

    @Override
    public int size() {
      return list.size();
    }

    @Override
    public boolean isEmpty() {
      return list.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
      return list.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
      return list.iterator();
    }

    @Override
    public Object[] toArray() {
      return list.toArray();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
      return list.toArray(a);
    }

    @Override
    public boolean add(final E e) {
      return list.add(e);
    }

    @Override
    public boolean remove(final Object o) {
      return list.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
      return list.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
      return list.addAll(c);
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends E> c) {
      return list.addAll(index, c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
      return list.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
      return list.retainAll(c);
    }

    @Override
    public void replaceAll(final UnaryOperator<E> operator) {
      list.replaceAll(operator);
    }

    @Override
    public void sort(final Comparator<? super E> c) {
      list.sort(c);
    }

    @Override
    public void clear() {
      list.clear();
    }

    @Override
    public E get(final int index) {
      return list.get(index);
    }

    @Override
    public E set(final int index, final E element) {
      return list.set(index, element);
    }

    @Override
    public void add(final int index, final E element) {
      list.add(index, element);
    }

    @Override
    public E remove(final int index) {
      return list.remove(index);
    }

    @Override
    public int indexOf(final Object o) {
      return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(final Object o) {
      return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
      return list.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
      return list.listIterator(index);
    }

    @Override
    public List<E> subList(final int fromIndex, final int toIndex) {
      return list.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<E> spliterator() {
      return list.spliterator();
    }

    @Override
    public <T> T[] toArray(final IntFunction<T[]> generator) {
      return list.toArray(generator);
    }

    @Override
    public boolean removeIf(final Predicate<? super E> filter) {
      return list.removeIf(filter);
    }

    @Override
    public Stream<E> stream() {
      return list.stream();
    }

    @Override
    public Stream<E> parallelStream() {
      return list.parallelStream();
    }

    @Override
    public void forEach(final Consumer<? super E> action) {
      list.forEach(action);
    }
  }
}
