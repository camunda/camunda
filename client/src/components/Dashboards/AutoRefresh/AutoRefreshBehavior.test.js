import React from 'react';
import {mount} from 'enzyme';

import AutoRefreshBehavior from './AutoRefreshBehavior';

jest.useFakeTimers();

const ReportSpy = jest.fn();
const dashboardSpy = jest.fn();
const interval = 600;

it('should register an interval with the specified interval duration and function', () => {
  mount(
    <AutoRefreshBehavior
      loadReportData={ReportSpy}
      renderDashboard={dashboardSpy}
      interval={interval}
    />
  );

  expect(setInterval).toHaveBeenCalledTimes(1);
});

it('should clear the interval when component is unmounted', () => {
  const node = mount(
    <AutoRefreshBehavior
      loadReportData={ReportSpy}
      renderDashboard={dashboardSpy}
      interval={interval}
    />
  );
  node.unmount();

  expect(clearInterval).toHaveBeenCalledTimes(1);
});

it('should update the interval when the interval property changes', () => {
  const node = mount(
    <AutoRefreshBehavior
      loadReportData={ReportSpy}
      renderDashboard={dashboardSpy}
      interval={interval}
    />
  );

  clearInterval.mockClear();
  node.setProps({interval: 1000});

  expect(clearInterval).toHaveBeenCalledTimes(1);
  expect(setInterval).toHaveBeenCalled();
});

it('should invoke the renderDashboard function after the interval duration ends', async () => {
  mount(
    <AutoRefreshBehavior
      loadReportData={ReportSpy}
      renderDashboard={dashboardSpy}
      interval={interval}
    />
  );
  jest.runTimersToTime(700);
  expect(await dashboardSpy).toHaveBeenCalled();
});

it('should invoke the loadReportData function after the interval duration ends', async () => {
  mount(
    <AutoRefreshBehavior
      loadReportData={ReportSpy}
      renderDashboard={dashboardSpy}
      interval={interval}
    />
  );
  jest.runTimersToTime(700);
  await dashboardSpy;
  expect(ReportSpy).toHaveBeenCalled();
});
