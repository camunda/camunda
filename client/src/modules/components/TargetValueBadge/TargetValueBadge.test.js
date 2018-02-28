import React from 'react';
import {mount} from 'enzyme';

import TargetValueBadge from './TargetValueBadge';
import {formatters} from 'services';

jest.mock('services', () => {
  const durationSpy = jest.fn();
  return {
    formatters: {
      duration: durationSpy
    }
  };
})

const viewer = {
  get: jest.fn().mockReturnThis(),
  remove: jest.fn(),
  add: jest.fn()
};

it('should add an overlay with the formatted target value', () => {
  formatters.duration.mockReturnValue('some duration');

  mount(<TargetValueBadge viewer={viewer} values={{a: {value: 8, unit: 'hours'}}} />);

  expect(viewer.add).toHaveBeenCalled();
  expect(viewer.add.mock.calls[0][2].html.textContent).toBe('some duration');
});
