import React from 'react';
import {shallow} from 'enzyme';

import ReportView from './ReportView';

it('should render ProcessReportView if the report type is process', () => {
  const report = {
    combined: false,
    reportType: 'process'
  };
  const node = shallow(<ReportView report={report} />);

  expect(node.find('ProcessReportView')).toBePresent();
});

it('should render DecisionReportView if the report type is decision', () => {
  const report = {
    combined: false,
    reportType: 'decision'
  };
  const node = shallow(<ReportView report={report} />);

  expect(node.find('DecisionReportView')).toBePresent();
});

it('should render CombinedReportView if the report is combined', () => {
  const report = {
    combined: true,
    reportType: 'process'
  };
  const node = shallow(<ReportView report={report} />);

  expect(node.find('CombinedReportView')).toBePresent();
});

it('should display an error message the report is defined', () => {
  const report = null;
  const node = shallow(<ReportView report={report} />);

  expect(node.find('Message')).toBePresent();
});
