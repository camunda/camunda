import React from 'react';
import {mount} from 'enzyme';

import DimensionSetter from './DimensionSetter';

it('should always set the height not to be less than the lowest report card', () => {
  const container = document.createElement('div');

  const tileDimensions = {
    outerHeight: 10
  };

  const report = {
    position: {x: 0, y: 0},
    dimensions: {width: 1, height: 3}
  };

  mount(
    <DimensionSetter
      emptyRows={0}
      container={container}
      tileDimensions={tileDimensions}
      reports={[report]}
    />
  );

  expect(parseInt(container.style.height, 10)).toBeGreaterThanOrEqual(
    tileDimensions.outerHeight * 3
  );
});
