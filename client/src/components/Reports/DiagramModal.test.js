/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {BPMNDiagram, DMNDiagram} from 'components';

import DiagramModal from './DiagramModal';

jest.mock('./newReport.json', () => {
  const report = {data: {configuration: {}}};
  return {new: report, 'new-decision': report};
});

const props = {
  report: {
    data: {
      configuration: {
        xml: 'xml data',
      },
    },
  },
  close: jest.fn(),
};

it('Should display BPMNDiagram with the report xml', () => {
  const node = shallow(<DiagramModal {...props} />);

  expect(node.find(BPMNDiagram).prop('xml')).toBe(props.report.data.configuration.xml);
});

it('Should display DMNDiagram with the report xml', () => {
  const node = shallow(
    <DiagramModal {...props} report={{...props.report, reportType: 'decision'}} />
  );

  expect(node.find(DMNDiagram).prop('xml')).toBe(props.report.data.configuration.xml);
});
