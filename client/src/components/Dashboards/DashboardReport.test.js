import React from 'react';
import {mount} from 'enzyme';

import DashboardReport from './DashboardReport';
import {loadReport} from './service';

jest.mock('./service', () => {return {
  loadReport: jest.fn()
}});
jest.mock('components', () => {return {
  ReportView: () => <div>ReportView</div>
}});

const report = {
  id: 'a'
};

it('should load the report provided by id', () => {
  mount(<DashboardReport report={report} />);

  expect(loadReport).toHaveBeenCalledWith('a');
});

it('should render the ReportView if data is loaded', async () => {
  loadReport.mockReturnValue('data');

  const node = mount(<DashboardReport report={report} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('ReportView');
});

it('should render an error message if report rendering went wrong', async () => {
  loadReport.mockReturnValue({errorMessage: 'I AM BROKEN!'});

  const node = mount(<DashboardReport report={report} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('I AM BROKEN!');
});

it('should contain the report name', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});

  const node = mount(<DashboardReport report={report} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('Report Name');
});

it('should render optional addons', async () => {
  loadReport.mockReturnValue('data');

    const node = mount(<DashboardReport report={report} addons={[
      <p key='textAddon'>I am an addon!</p>
    ]} />);

    await node.instance().loadReportData();

    expect(node).toIncludeText('I am an addon!');
});
