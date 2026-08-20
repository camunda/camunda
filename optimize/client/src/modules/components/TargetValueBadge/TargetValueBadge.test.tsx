/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastCleanup, runLastEffect} from '__mocks__/react';
import {mount} from 'enzyme';

import TargetValueBadge, {Overlay} from './TargetValueBadge';
import {formatters} from 'services';

jest.mock('services', () => {
  const durationSpy = jest.fn();
  return {
    formatters: {
      duration: durationSpy,
    },
  };
});

const viewer = {
  get: jest.fn().mockReturnThis(),
  remove: jest.fn(),
  add: jest.fn(),
} as jest.MockedObject<Overlay>;

(formatters.duration as jest.Mock).mockReturnValue('some duration');

it('should add an overlay with the formatted target value', () => {
  mount(<TargetValueBadge viewer={viewer} values={{a: {value: 8, unit: 'hours'}}} />);

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0]?.[2].html.textContent).toBe('some duration');
});

it('should remove overlays when unmounting', () => {
  const node = mount(<TargetValueBadge viewer={viewer} values={{a: {value: 8, unit: 'hours'}}} />);

  viewer.remove.mockClear();
  node.unmount();

  runLastEffect();
  runLastCleanup();

  expect(viewer.remove).toHaveBeenCalled();
});
