import React from 'react';
import {mount} from 'enzyme';

import DashboardReport from './DashboardReport';
import {loadReport, getReportName} from '../service';


jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>
    },
    Link: ({children, to, onClick, id}) => {
      return <a id={id} href={to} onClick={onClick}>{children}</a>
    }
  }
});

jest.mock('../service', () => {return {
  loadReport: jest.fn(),
  getReportName: jest.fn()
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
  getReportName.mockReturnValue('Report Name');

  const node = mount(<DashboardReport report={report} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('Report Name');
});

it('should render optional addons', async () => {
  loadReport.mockReturnValue('data');

  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = mount(<DashboardReport report={report} addons={[
    <TextRenderer key='textAddon'>I am an addon!</TextRenderer>
  ]} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('I am an addon!');
});
