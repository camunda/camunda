/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Publisher from './publisher';

import {
  MOCK_TOPICS,
  topicFoo,
  topicBar,
  callbackMockOne,
  callbackMockTwo
} from './publisher.setup';

describe('Publisher', () => {
  let publisher;

  beforeEach(() => {
    publisher = new Publisher(MOCK_TOPICS);
  });

  describe('subscribe', () => {
    it('should allow to subscribe to one topcics', () => {
      // when
      publisher.subscribe({[topicFoo]: callbackMockOne});

      // then
      expect(publisher.subscriptions).toEqual({
        [topicFoo]: [callbackMockOne]
      });
    });

    it('should allow to subscribe to multiple subscriptions', () => {
      // when
      publisher.subscribe({
        [topicFoo]: callbackMockOne,
        [topicBar]: callbackMockOne
      });

      // then
      expect(publisher.subscriptions).toEqual({
        [topicFoo]: [callbackMockOne],
        [topicBar]: [callbackMockOne]
      });
    });

    it('should add callbacks to already existing subscriptions', () => {
      // when
      publisher.subscribe({[topicFoo]: callbackMockOne});
      publisher.subscribe({[topicFoo]: callbackMockTwo});

      // then
      expect(publisher.subscriptions).toEqual({
        [topicFoo]: [callbackMockOne, callbackMockTwo]
      });
    });

    it('should print an warning if some one subscribes to unknown topic', () => {
      // given
      const topic = 'SOME_UNKNOWN_TOPIC';
      global.console = {
        warn: jest.fn()
      };

      // when
      publisher.subscribe({[topic]: jest.fn()});

      // then
      expect(global.console.warn).toHaveBeenCalled();
    });
  });

  describe('unsubscribe', () => {
    it('should allow to UN-subscribe one or multiple subscriptions', () => {
      // given

      const subscriptions = {
        [topicFoo]: callbackMockOne,
        [topicBar]: callbackMockTwo
      };
      publisher.subscribe(subscriptions);

      // when
      publisher.unsubscribe(subscriptions);

      // then
      expect(publisher.subscriptions).toEqual({});
    });

    it('should allow to UN-subscribe to a topic others still listen to', () => {
      // given

      const subscription = {[topicFoo]: callbackMockOne};
      const otherSubscriptions = {[topicFoo]: callbackMockTwo};

      publisher.subscribe(subscription);
      publisher.subscribe(otherSubscriptions);

      // when
      publisher.unsubscribe(subscription);

      // then
      expect(publisher.subscriptions).toEqual({[topicFoo]: [callbackMockTwo]});
    });
  });

  describe('publish', () => {
    it('should publish value for a topic', () => {
      const publishedValue = 'StringValue';

      // when 1
      publisher.subscribe({[topicFoo]: callbackMockOne});

      // when 2
      publisher.publish(topicFoo, publishedValue);

      // then
      expect(callbackMockOne).toHaveBeenCalledWith(publishedValue);
    });

    it('should publish value to all callbacks of a topic', () => {
      // given
      const publishedValue = 'StringValue';

      // when 1
      publisher.subscribe({[topicFoo]: callbackMockOne});
      publisher.subscribe({[topicFoo]: callbackMockTwo});

      // when 2
      publisher.publish(topicFoo, publishedValue);

      // then
      expect(callbackMockOne).toHaveBeenCalledWith(publishedValue);
      expect(callbackMockTwo).toHaveBeenCalledWith(publishedValue);
    });

    it('should print an warning if some one publishes to unknown topic', () => {
      // given
      const topic = 'SOME_UNKNOWN_TOPIC';
      const publishedValue = 'StringValue';

      global.console = {
        warn: jest.fn()
      };

      // when 2
      publisher.publish(topic, publishedValue);

      // then
      expect(global.console.warn).toHaveBeenCalled();
    });
  });
});
