/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {mount} from 'enzyme';

import DashboardReport from './DashboardReport';

jest.mock('./ExternalReport', () => {
  const actual = jest.requireActual('./ExternalReport');
  const ExternalReport = ({children}) => <span>ExternalReport: {children()}</span>;
  ExternalReport.isExternalReport = actual.default.isExternalReport;
  return ExternalReport;
});
jest.mock('./TextReport', () => {
  const actual = jest.requireActual('./TextReport');
  const TextReport = ({children}) => <span>TextReport: {children()}</span>;
  TextReport.isTextReport = actual.default.isTextReport;
  return TextReport;
});
jest.mock('./OptimizeReport', () => ({children}) => <span>OptimizeReport: {children()}</span>);

const props = {
  report: {
    id: 'a',
  },
};

it('should render optional addons', () => {
  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = mount(
    <DashboardReport
      {...props}
      addons={[<TextRenderer key="textAddon">I am an addon!</TextRenderer>]}
    />
  );

  expect(node).toIncludeText('I am an addon!');
});

it('should pass properties to report addons', () => {
  const PropsRenderer = (props) => <p>{JSON.stringify(Object.keys(props))}</p>;

  const node = mount(
    <DashboardReport {...props} addons={[<PropsRenderer key="propsRenderer" />]} />
  );

  expect(node).toIncludeText('report');
  expect(node).toIncludeText('tileDimensions');
});
