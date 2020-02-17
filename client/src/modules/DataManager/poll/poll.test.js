/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Poll from './poll';

jest.useFakeTimers();

const POLL_DELAY = 5000;

describe('Poll', () => {
  let poll;

  beforeEach(() => {
    poll = new Poll(POLL_DELAY);
  });

  afterEach(() => {
    jest.clearAllTimers();
  });

  it('should register for polling', () => {
    // given
    const topicCallback = jest.fn();

    // when
    poll.register('TOPIC', topicCallback);
    jest.advanceTimersByTime(POLL_DELAY);

    // then
    expect(topicCallback).toHaveBeenCalledTimes(1);
  });

  it('should register two different topics for polling', () => {
    // given
    const topic1Callback = jest.fn();
    const topic2Callback = jest.fn();

    // when
    poll.register('TOPIC_1', topic1Callback);
    poll.register('TOPIC_2', topic2Callback);

    jest.advanceTimersByTime(POLL_DELAY);

    // then
    expect(Object.values(poll.callbacks)).toHaveLength(2);
    expect(topic1Callback).toHaveBeenCalledTimes(1);
    expect(topic2Callback).toHaveBeenCalledTimes(1);
  });

  it('should poll continously', () => {
    // given
    const topicCallback = jest.fn();

    // when
    poll.register('TOPIC', topicCallback);
    jest.advanceTimersByTime(10 * POLL_DELAY);

    // then
    expect(topicCallback).toHaveBeenCalledTimes(10);
  });

  it('should not register the same topic twice', () => {
    // given
    const topicCallback = jest.fn();

    // when
    poll.register('TOPIC', topicCallback);
    poll.register('TOPIC', topicCallback);
    jest.advanceTimersByTime(POLL_DELAY);

    //then
    expect(Object.values(poll.callbacks)).toHaveLength(1);
    expect(topicCallback).toHaveBeenCalledTimes(1);
  });

  it('should unregister', () => {
    //when
    const topicCallback = jest.fn();
    poll.register('TOPIC', topicCallback);
    poll.unregister('TOPIC');

    jest.advanceTimersByTime(10 * POLL_DELAY);

    expect(topicCallback).not.toHaveBeenCalled();
    expect(poll.callbacks).toEqual({});
  });
});
