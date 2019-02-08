import React from 'react';
import {shallow} from 'enzyme';

import ReportRenderer from './ReportRenderer';

it('should render ProcessReportRenderer if the report type is process', () => {
  const report = {
    combined: false,
    reportType: 'process'
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('ProcessReportRenderer')).toBePresent();
});

it('should render DecisionReportRenderer if the report type is decision', () => {
  const report = {
    combined: false,
    reportType: 'decision'
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('DecisionReportRenderer')).toBePresent();
});

it('should render CombinedReportRenderer if the report is combined', () => {
  const report = {
    combined: true,
    reportType: 'process'
  };
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('CombinedReportRenderer')).toBePresent();
});

it('should display an error message the report is defined', () => {
  const report = null;
  const node = shallow(<ReportRenderer report={report} />);

  expect(node.find('Message')).toBePresent();
});
