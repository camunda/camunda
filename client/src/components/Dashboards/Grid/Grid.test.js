import React from 'react';
import {mount} from 'enzyme';

import Grid from './Grid';

const tileDimensions = {
  outerHeight: 10,
  innerHeight: 5,
  outerWidth: 10,
  innerWidth: 5
};

it('should set the background image properties of the provided container', () => {
  const container = document.createElement('div');
  mount(<Grid container={container} tileDimensions={tileDimensions} reports={[]} />);

  expect(container.style.backgroundImage).toContain('image/svg+xml');
});
