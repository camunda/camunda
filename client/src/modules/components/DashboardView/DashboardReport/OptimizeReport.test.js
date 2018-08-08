import React from 'react';
import {mount} from 'enzyme';

import OptimizeReport from './OptimizeReport';

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to, onClick, id}) => {
      return (
        <a id={id} href={to} onClick={onClick}>
          {children}
        </a>
      );
    }
  };
});

jest.mock('components', () => {
  return {
    ReportView: () => <div>ReportView</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

const loadReport = jest.fn();

const props = {
  report: {
    id: 'a'
  },
  loadReport
};

it('should load the report provided by id', () => {
  mount(<OptimizeReport {...props} />);

  expect(loadReport).toHaveBeenCalledWith({...props.report});
});

it('should render the ReportView if data is loaded', async () => {
  loadReport.mockReturnValue('data');

  const node = mount(<OptimizeReport {...props} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('ReportView');
});

it('should render an error message if report rendering went wrong', async () => {
  loadReport.mockReturnValue({errorMessage: 'I AM BROKEN!'});

  const node = mount(<OptimizeReport {...props} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('I AM BROKEN!');
});

it('should contain the report name', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = mount(<OptimizeReport {...props} />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = mount(<OptimizeReport {...props} />);

  await node.instance().loadReportData();
  node.update();

  expect(node.find('a')).toIncludeText('Report Name');
  expect(node.find('a')).toHaveProp('href', '/report/a');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = mount(<OptimizeReport {...props} disableNameLink />);

  await node.instance().loadReportData();
  node.update();

  expect(node.find('a')).not.toBePresent();
  expect(node).toIncludeText('Report Name');
});

it('should display the name of a failing report', async () => {
  loadReport.mockReturnValue({
    errorMessage: 'Is failing',
    reportDefinition: {name: 'Failing Name'}
  });
  const node = mount(<OptimizeReport {...props} disableNameLink />);

  await node.instance().loadReportData();

  expect(node).toIncludeText('Failing Name');
});
