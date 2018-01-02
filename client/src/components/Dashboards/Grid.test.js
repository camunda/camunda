import React from 'react';
import {mount} from 'enzyme';

import Grid from './Grid';

const tileDimensions = {
  outerHeight: 10,
  innerHeight: 5,
  outerWidth: 10,
  innerWidth: 5
};

it('should set the height and background image properties of the provided container', () => {
  const container = document.createElement('div');
  mount(<Grid container={container} tileDimensions={tileDimensions} reports={[]} />);

  expect(container.style.height).toBeDefined();
  expect(container.style.height).not.toBe('0px');
  expect(container.style.backgroundImage).toContain('image/svg+xml');
});

it('should always set the height to be bigger than the lowest report card', () => {
  const container = document.createElement('div');
  const report = {
    position: {x: 0, y: 0},
    dimensions: {width: 1, height: 3}
  };
  mount(<Grid container={container} tileDimensions={tileDimensions} reports={[report]} />);

  expect(parseInt(container.style.height, 10)).toBeGreaterThan(tileDimensions.outerHeight * 3);
});
