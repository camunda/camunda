/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

public class EventCountResponseDto {

  private String group;
  private String source;
  private String eventName;
  private String eventLabel;
  private Long count;
  private boolean suggested = false;

  public EventCountResponseDto(
      final String group,
      final String source,
      final String eventName,
      final String eventLabel,
      final Long count,
      final boolean suggested) {
    this.group = group;
    this.source = source;
    this.eventName = eventName;
    this.eventLabel = eventLabel;
    this.count = count;
    this.suggested = suggested;
  }

  public EventCountResponseDto() {
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(final String group) {
    this.group = group;
  }

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    if (source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }

    this.source = source;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(final String eventName) {
    if (eventName == null) {
      throw new IllegalArgumentException("eventName cannot be null");
    }

    this.eventName = eventName;
  }

  public String getEventLabel() {
    return eventLabel;
  }

  public void setEventLabel(final String eventLabel) {
    this.eventLabel = eventLabel;
  }

  public Long getCount() {
    return count;
  }

  public void setCount(final Long count) {
    this.count = count;
  }

  public boolean isSuggested() {
    return suggested;
  }

  public void setSuggested(final boolean suggested) {
    this.suggested = suggested;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventCountResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $group = getGroup();
    result = result * PRIME + ($group == null ? 43 : $group.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $eventName = getEventName();
    result = result * PRIME + ($eventName == null ? 43 : $eventName.hashCode());
    final Object $eventLabel = getEventLabel();
    result = result * PRIME + ($eventLabel == null ? 43 : $eventLabel.hashCode());
    final Object $count = getCount();
    result = result * PRIME + ($count == null ? 43 : $count.hashCode());
    result = result * PRIME + (isSuggested() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventCountResponseDto)) {
      return false;
    }
    final EventCountResponseDto other = (EventCountResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$group = getGroup();
    final Object other$group = other.getGroup();
    if (this$group == null ? other$group != null : !this$group.equals(other$group)) {
      return false;
    }
    final Object this$source = getSource();
    final Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
      return false;
    }
    final Object this$eventName = getEventName();
    final Object other$eventName = other.getEventName();
    if (this$eventName == null ? other$eventName != null
        : !this$eventName.equals(other$eventName)) {
      return false;
    }
    final Object this$eventLabel = getEventLabel();
    final Object other$eventLabel = other.getEventLabel();
    if (this$eventLabel == null ? other$eventLabel != null
        : !this$eventLabel.equals(other$eventLabel)) {
      return false;
    }
    final Object this$count = getCount();
    final Object other$count = other.getCount();
    if (this$count == null ? other$count != null : !this$count.equals(other$count)) {
      return false;
    }
    if (isSuggested() != other.isSuggested()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventCountResponseDto(group=" + getGroup() + ", source=" + getSource()
        + ", eventName=" + getEventName() + ", eventLabel=" + getEventLabel() + ", count="
        + getCount() + ", suggested=" + isSuggested() + ")";
  }

  private static boolean $default$suggested() {
    return false;
  }

  public static EventCountResponseDtoBuilder builder() {
    return new EventCountResponseDtoBuilder();
  }

  public EventCountResponseDtoBuilder toBuilder() {
    return new EventCountResponseDtoBuilder().group(group).source(source)
        .eventName(eventName).eventLabel(eventLabel).count(count)
        .suggested(suggested);
  }

  public static final class Fields {

    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String eventLabel = "eventLabel";
    public static final String count = "count";
    public static final String suggested = "suggested";
  }

  public static class EventCountResponseDtoBuilder {

    private String group;
    private String source;
    private String eventName;
    private String eventLabel;
    private Long count;
    private boolean suggested$value;
    private boolean suggested$set;

    EventCountResponseDtoBuilder() {
    }

    public EventCountResponseDtoBuilder group(final String group) {
      this.group = group;
      return this;
    }

    public EventCountResponseDtoBuilder source(final String source) {
      if (source == null) {
        throw new IllegalArgumentException("source cannot be null");
      }

      this.source = source;
      return this;
    }

    public EventCountResponseDtoBuilder eventName(final String eventName) {
      if (eventName == null) {
        throw new IllegalArgumentException("eventName cannot be null");
      }

      this.eventName = eventName;
      return this;
    }

    public EventCountResponseDtoBuilder eventLabel(final String eventLabel) {
      this.eventLabel = eventLabel;
      return this;
    }

    public EventCountResponseDtoBuilder count(final Long count) {
      this.count = count;
      return this;
    }

    public EventCountResponseDtoBuilder suggested(final boolean suggested) {
      suggested$value = suggested;
      suggested$set = true;
      return this;
    }

    public EventCountResponseDto build() {
      boolean suggested$value = this.suggested$value;
      if (!suggested$set) {
        suggested$value = EventCountResponseDto.$default$suggested();
      }
      return new EventCountResponseDto(group, source, eventName, eventLabel,
          count, suggested$value);
    }

    @Override
    public String toString() {
      return "EventCountResponseDto.EventCountResponseDtoBuilder(group=" + group + ", source="
          + source + ", eventName=" + eventName + ", eventLabel=" + eventLabel
          + ", count=" + count + ", suggested$value=" + suggested$value + ")";
    }
  }
}
