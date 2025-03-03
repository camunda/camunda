/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {runLastEffect} from '__mocks__/react';

import EntityDescription from './EntityDescription';
import {useRef} from 'react';

jest.useFakeTimers();

jest.mock('react', () => {
  const outstandingEffects: (() => void)[] = [];
  const useRef = jest.fn().mockReturnValue({
    current: {
      getBoundingClientRect: () => ({width: 200}),
      parentElement: {getBoundingClientRect: () => ({width: 400})},
      nextElementSibling: {getBoundingClientRect: () => ({width: 20})},
    },
  });
  return {
    ...jest.requireActual('react-18'),
    useEffect: (fn: () => void) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()?.();
      }
    },
    useRef,
  };
});

it('should render EntityDescription', () => {
  const node = shallow(<EntityDescription description={'some text'} />);

  expect(node.find('.EntityDescription .description').text()).toBe('some text');
  expect(node.find('.toggle')).toBeDefined();
});

it('shouldnt render description container when there is no description', () => {
  const node = shallow(<EntityDescription description={null} />);

  expect(node.find('.EntityDescription .description')).not.toExist();
});

it('should handle change in edit mode', () => {
  const spy = jest.fn();
  const node = shallow(<EntityDescription description={null} onEdit={spy} />);

  const addEditButton = node.find('.EntityDescription .add').dive();

  expect(node.find('.EntityDescription .description')).not.toExist();
  expect(addEditButton.text()).toContain('Add description');

  addEditButton.simulate('click');

  node.find('.EntityDescriptionEditModal TextEditor').simulate('change', 'new text');

  node.find('.EntityDescriptionEditModal .confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith('new text');
});

it('should reset description on cancel', () => {
  const spy = jest.fn();
  const node = shallow(<EntityDescription description={'description'} onEdit={spy} />);

  node.find('.EntityDescription .edit').simulate('click');

  node.find('.EntityDescriptionEditModal TextEditor').simulate('change', 'new text');

  node.find('.EntityDescriptionEditModal .cancel').simulate('click');

  expect(spy).not.toHaveBeenCalled();
  expect(node.find('.EntityDescription .description').text()).toBe('description');
});

it('should hide more/less button when text is not longer then container', () => {
  const node = shallow(<EntityDescription description={'description'} />);
  runLastEffect();

  expect(node.find('.toggle')).not.toExist();
});

it('should show more/less button when text is not longer then container', () => {
  (useRef as jest.Mock).mockReturnValueOnce({
    current: {
      getBoundingClientRect: () => ({width: 350}),
      parentElement: {getBoundingClientRect: () => ({width: 400})},
      nextElementSibling: {getBoundingClientRect: () => ({width: 20})},
    },
  });
  const node = shallow(<EntityDescription description={'description'} />);
  runLastEffect();
  jest.runAllTimers();

  expect(node.find('.toggle')).toExist();
});

it('should toggle show more/less text', () => {
  (useRef as jest.Mock).mockReturnValueOnce({
    current: {
      getBoundingClientRect: () => ({width: 350}),
      parentElement: {getBoundingClientRect: () => ({width: 400})},
      nextElementSibling: {getBoundingClientRect: () => ({width: 20})},
    },
  });
  const node = shallow(<EntityDescription description={'description'} />);
  runLastEffect();
  jest.runAllTimers();

  expect(node.find('.toggle').text()).toBe('More');

  node.find('.toggle').simulate('click');

  expect(node.find('.toggle').text()).toBe('Less');
});
