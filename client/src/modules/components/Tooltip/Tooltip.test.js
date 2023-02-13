/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect, useRef} from 'react';
import {mount, shallow} from 'enzyme';
import {act} from 'react-dom/test-utils';

import {getNonOverflowingValues} from './service';
import Tooltip from './Tooltip';

jest.useFakeTimers();

jest.mock('react', () => {
  const outstandingEffects = [];
  const mUseRef = jest.fn().mockReturnValue({current: {}});
  return {
    ...jest.requireActual('react'),
    useEffect: (fn) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()();
      }
    },
    useRef: mUseRef,
  };
});

jest.mock('./service', () => ({getNonOverflowingValues: jest.fn()}));

const element = {
  getBoundingClientRect: () => ({
    x: 0,
    y: 0,
    width: 100,
    height: 100,
    top: 0,
    bottom: 100,
  }),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render children content if no tooltip content is provided', () => {
  const node = shallow(<Tooltip>child content</Tooltip>);

  expect(node).toIncludeText('child content');
});

it('should render child content with mouse handlers', () => {
  const node = shallow(
    <Tooltip content="tooltip content">
      <p>child content</p>
    </Tooltip>
  );

  expect(node.find('p')).toHaveProp('onMouseEnter');
  expect(node.find('p')).toHaveProp('onMouseLeave');
});

it('should render the tooltip content', () => {
  const node = shallow(
    <Tooltip content="tooltip content">
      <p>child content</p>
    </Tooltip>
  );

  node.find('p').simulate('mouseEnter', {currentTarget: element});
  jest.runAllTimers();

  expect(node.find('.Tooltip')).toIncludeText('tooltip content');
});

it('should add the provided position, theme and alignment to the tooltip class', () => {
  const node = shallow(
    <Tooltip content="tooltip content" theme="dark" position="bottom" align="left">
      <p>child content</p>
    </Tooltip>
  );

  node.find('p').simulate('mouseEnter', {currentTarget: element});
  jest.runAllTimers();

  expect(node.find('.Tooltip')).toHaveClassName('dark');
  expect(node.find('.Tooltip')).toHaveClassName('bottom');
  expect(node.find('.Tooltip')).toHaveClassName('left');
});

it('should not render tooltip content if overflowOnly is set and children do not overflow', () => {
  const node = shallow(
    <Tooltip content="tooltip content" overflowOnly>
      <p>child content</p>
    </Tooltip>
  );

  node
    .find('p')
    .simulate('mouseEnter', {currentTarget: {...element, scrollWidth: 100, clientWidth: 100}});
  jest.runAllTimers();

  expect(node.find('.Tooltip')).not.toExist();
});

it('should render tooltip content for overflowing children', () => {
  const node = shallow(
    <Tooltip content="tooltip content" overflowOnly>
      <p>child content</p>
    </Tooltip>
  );

  node
    .find('p')
    .simulate('mouseEnter', {currentTarget: {...element, scrollWidth: 150, clientWidth: 100}});
  jest.runAllTimers();

  expect(node.find('.Tooltip')).toExist();
});

it('should open the tooltip after a delay', () => {
  const node = shallow(
    <Tooltip content="tooltip content" delay={1000}>
      <p>child content</p>
    </Tooltip>
  );

  node.find('p').simulate('mouseEnter', {currentTarget: element});

  jest.advanceTimersByTime(500);
  expect(node.find('.Tooltip')).not.toExist();

  jest.advanceTimersByTime(500);
  expect(node.find('.Tooltip')).toExist();
});

it('should call onMouseEnter and onMouseLeave functions if specified', () => {
  const enter = jest.fn();
  const leave = jest.fn();

  const node = shallow(
    <Tooltip content="tooltip content">
      <p onMouseEnter={enter} onMouseLeave={leave}>
        child content
      </p>
    </Tooltip>
  );

  const evt = {currentTarget: element};
  node.find('p').simulate('mouseEnter', evt);
  expect(enter).toHaveBeenCalledWith(evt);

  node.find('p').simulate('mouseLeave', evt);
  expect(leave).toHaveBeenCalledWith(evt);
});

it('should invoke getNonOverflowingValues to adjust tooltip alignment, position and styles on open', () => {
  useRef.mockReturnValue({current: {}});
  getNonOverflowingValues.mockReturnValue({
    newAlign: 'left',
    newPosition: 'bottom',
    width: '200',
    left: 10,
    top: 20,
  });

  const node = shallow(
    <Tooltip content="tooltip content" position="top" align="right">
      <p>child content</p>
    </Tooltip>
  );

  runLastEffect();
  node.find('p').simulate('mouseEnter', {currentTarget: element});
  jest.runAllTimers();

  expect(node.find('.Tooltip').prop('style').left).toBe('10px');
  expect(node.find('.Tooltip')).toHaveClassName('bottom');
  expect(node.find('.Tooltip')).toHaveClassName('left');
});

it('should keep the tooltip open when the mouse is inside it', () => {
  const node = shallow(
    <Tooltip content="tooltip content">
      <p>child content</p>
    </Tooltip>
  );

  node.find('p').simulate('mouseEnter', {currentTarget: element});
  jest.runAllTimers();
  node.find('p').simulate('mouseLeave', {currentTarget: element});
  node.find('.Tooltip').simulate('mouseEnter');
  jest.runAllTimers();

  expect(node.find('.Tooltip')).toIncludeText('tooltip content');

  node.find('.Tooltip').simulate('mouseLeave');

  expect(node.find('.Tooltip')).not.toExist();
});

it('should stop event from propagating when clicked inside the tooltip', () => {
  const spy = jest.fn();
  const node = mount(
    <div onClick={spy}>
      <Tooltip content="tooltip content">
        <p>child content</p>
      </Tooltip>
    </div>
  );
  act(() => {
    node.find('p').simulate('mouseEnter', {currentTarget: element});
    jest.runAllTimers();
    node.find('p').simulate('mouseLeave', {currentTarget: element});
    node.find('.Tooltip').simulate('mouseEnter');
    jest.runAllTimers();
    node.find('.Tooltip').simulate('click', new Event('click'));

    expect(spy).not.toHaveBeenCalled();
  });
});
