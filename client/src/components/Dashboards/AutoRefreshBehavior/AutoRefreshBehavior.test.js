import React from 'react';
import {mount} from 'enzyme';

import AutoRefreshBehavior from './AutoRefreshBehavior';

jest.useFakeTimers();

const fct = jest.fn();
const interval = 600;

it('should register an interval with the specified interval duration and function', () => {
  mount(<AutoRefreshBehavior loadReportData={fct} interval={interval} />);

  expect(setInterval).toHaveBeenCalledTimes(1);
});

it('should clear the interval when component is unmounted', () => {
  const node = mount(<AutoRefreshBehavior loadReportData={fct} interval={interval} />);
  node.unmount();

  expect(clearInterval).toHaveBeenCalledTimes(1);
});

it('should update the interval when the interval property changes', () => {
  const node = mount(<AutoRefreshBehavior loadReportData={fct} interval={interval} />);

  clearInterval.mockClear();
  node.setProps({interval: 1000});

  expect(clearInterval).toHaveBeenCalledTimes(1);
  expect(setInterval).toHaveBeenCalledWith(fct, 1000);
});
