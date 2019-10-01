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

  it('should start a timer', () => {
    poll.start(jest.fn());

    expect(clearTimeout).not.toHaveBeenCalled();

    expect(setTimeout).toHaveBeenCalledTimes(1);
    expect(setTimeout).toHaveBeenLastCalledWith(
      expect.any(Function),
      POLL_DELAY
    );
  });

  it('should overwrite existing timer', () => {
    //given
    poll.start(jest.fn());
    expect(clearTimeout).not.toHaveBeenCalled();
    expect(poll.pollTimer).toEqual(expect.any(Number));

    //when
    poll.start(jest.fn());

    //then
    expect(clearTimeout).toHaveBeenCalled();
  });

  it('should clear a timer', () => {
    //when
    poll.clear();

    //then
    expect(clearTimeout).toHaveBeenCalled();
    expect(poll.pollTimer).toEqual(null);
  });
});
