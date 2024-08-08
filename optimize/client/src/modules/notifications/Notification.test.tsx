/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import Notification from './Notification';

jest.useFakeTimers();

it('should render text provided in config', () => {
  const node = shallow(
    <Notification config={{text: 'Sample', stayOpen: true}} remove={jest.fn()} />
  );

  expect(node).toMatchSnapshot();
});

it('should call the provided remove function after a while', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample'}} remove={spy} />);

  runLastEffect();
  jest.runAllTimers();

  expect(spy).toHaveBeenCalled();
});

it('should not remove the Notification if it should stay open', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample', stayOpen: true}} remove={spy} />);

  runLastEffect();
  jest.runAllTimers();

  expect(spy).not.toHaveBeenCalled();
});

it('should not remove the Notification if the user interacts with it', () => {
  const spy = jest.fn();
  const node = shallow(<Notification config={{text: 'Sample', stayOpen: true}} remove={spy} />);

  node.simulate('click');

  jest.runAllTimers();

  expect(spy).not.toHaveBeenCalled();
});

it('should remove the Notification after the specified delay', () => {
  const spy = jest.fn();
  shallow(<Notification config={{text: 'Sample', duration: 1234}} remove={spy} />);

  runLastEffect();
  jest.advanceTimersByTime(1230);

  expect(spy).not.toHaveBeenCalled();

  jest.advanceTimersByTime(1235);

  expect(spy).toHaveBeenCalled();
});
