/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ExternalReport from './ExternalReport';

it('should include an iframe with the provided external url', () => {
  const node = mount(<ExternalReport report={{configuration: {external: 'externalURL'}}} />);

  const iframe = node.find('iframe');

  expect(iframe).toExist();
  expect(iframe).toHaveProp('src', 'externalURL');
});

it('should update the iframe key to reload it when loadReportData function is called ', async () => {
  const node = mount(<ExternalReport report={{configuration: {external: 'externalURL'}}} />);

  await node.instance().reloadReport();

  expect(node.state().reloadState).toBe(1);
});

describe('ExternalReport.isExternalReport', () => {
  it('should return true if report is external', () => {
    expect(ExternalReport.isExternalReport({configuration: {external: 'externalUrl'}})).toBe(true);
  });

  it('should return false if report is not external', () => {
    expect(ExternalReport.isExternalReport({configuration: {text: 'text'}})).toBe(false);
  });
});
