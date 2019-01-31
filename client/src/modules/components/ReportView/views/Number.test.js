import React from 'react';
import {shallow} from 'enzyme';

import Number from './Number';
import ProgressBar from './ProgressBar';
import ReportBlankSlate from '../ReportBlankSlate';

jest.mock('services', () => {
  return {
    formatters: {
      convertDurationToSingleNumber: () => 12
    },
    isDurationValue: data => typeof data !== 'number'
  };
});

jest.mock('./ProgressBar', () => () => <div>ProgressBar</div>);

const report = {
  reportType: 'process',
  combined: false,
  data: {
    configuration: {
      targetValue: {active: false}
    },
    view: {
      operation: 'count'
    },
    visualization: 'Number'
  },
  result: 1234
};

it('should display the number provided per data property', () => {
  const node = shallow(<Number {...report} formatter={v => v} />);
  expect(node).toIncludeText('1234');
});

it('should display an error message if the data does not have the correct format', () => {
  const node = shallow(
    <Number {...report} result={{foo: 1234}} errorMessage="Error" formatter={v => v} />
  );

  expect(node.find(ReportBlankSlate)).toBePresent();
});

it('should display an error message if no data is provided', () => {
  const node = shallow(
    <Number {...report} result={null} errorMessage="Error" formatter={v => v} />
  );

  expect(node.find(ReportBlankSlate)).toBePresent();
});

it('should not display an error message if data is valid', () => {
  const node = shallow(<Number {...report} errorMessage="Error" formatter={v => v} />);

  expect(node.find(ReportBlankSlate)).not.toBePresent();
});

it('should format data according to the provided formatter', () => {
  const node = shallow(<Number {...report} formatter={v => 2 * v} />);

  expect(node).toIncludeText('246');
});

it('should display a progress bar if target values are active', () => {
  const node = shallow(
    <Number
      {...report}
      data={{
        ...report.data,
        configuration: {targetValue: {active: true, countProgress: {baseline: '0', target: '12'}}}
      }}
      formatter={v => 2 * v}
    />
  );

  expect(node.find(ProgressBar)).toBePresent();
});
