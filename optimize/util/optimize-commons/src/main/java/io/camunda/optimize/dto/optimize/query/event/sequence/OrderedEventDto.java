/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;

public class OrderedEventDto extends EventDto {

  private Long orderCounter;

  protected OrderedEventDto(final OrderedEventDtoBuilder<?, ?> b) {
    super(b);
    orderCounter = b.orderCounter;
  }

  public Long getOrderCounter() {
    return orderCounter;
  }

  public void setOrderCounter(final Long orderCounter) {
    this.orderCounter = orderCounter;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof OrderedEventDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $orderCounter = getOrderCounter();
    result = result * PRIME + ($orderCounter == null ? 43 : $orderCounter.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OrderedEventDto)) {
      return false;
    }
    final OrderedEventDto other = (OrderedEventDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$orderCounter = getOrderCounter();
    final Object other$orderCounter = other.getOrderCounter();
    if (this$orderCounter == null
        ? other$orderCounter != null
        : !this$orderCounter.equals(other$orderCounter)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "OrderedEventDto(orderCounter=" + getOrderCounter() + ")";
  }

  public static OrderedEventDtoBuilder<?, ?> builder() {
    return new OrderedEventDtoBuilderImpl();
  }

  public abstract static class OrderedEventDtoBuilder<
          C extends OrderedEventDto, B extends OrderedEventDtoBuilder<C, B>>
      extends EventDtoBuilder<C, B> {

    private Long orderCounter;

    public B orderCounter(final Long orderCounter) {
      this.orderCounter = orderCounter;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "OrderedEventDto.OrderedEventDtoBuilder(super="
          + super.toString()
          + ", orderCounter="
          + orderCounter
          + ")";
    }
  }

  private static final class OrderedEventDtoBuilderImpl
      extends OrderedEventDtoBuilder<OrderedEventDto, OrderedEventDtoBuilderImpl> {

    private OrderedEventDtoBuilderImpl() {}

    @Override
    protected OrderedEventDtoBuilderImpl self() {
      return this;
    }

    @Override
    public OrderedEventDto build() {
      return new OrderedEventDto(this);
    }
  }
}
