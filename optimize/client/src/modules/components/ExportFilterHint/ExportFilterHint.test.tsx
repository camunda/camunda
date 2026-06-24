/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {useUiConfig} from 'hooks';

import {DocsLink} from '../DocsLink';

import ExportFilterHint from './ExportFilterHint';

jest.mock('hooks', () => ({
  useUiConfig: jest.fn(() => ({optimizeDatabase: 'elasticsearch'})),
}));

const esPage =
  'self-managed/components/orchestration-cluster/zeebe/exporters/elasticsearch-exporter/';
const osPage = 'self-managed/components/orchestration-cluster/zeebe/exporters/opensearch-exporter/';

it('should render an info toggletip with the localized hint text and icon label', () => {
  const node = shallow(<ExportFilterHint variant="variable" />);

  expect(node.find('Toggletip')).toExist();
  expect(node.find('ToggletipButton').prop('label')).toContain('export filtering');
  expect(node.find('span').text()).toContain('exporter configuration');
});

it('should link the variable variant to the variable-name filter documentation', () => {
  const node = shallow(<ExportFilterHint variant="variable" />);

  expect(node.find(DocsLink).prop('location')).toBe(esPage + '#variable-name-filters');
});

it('should link the report-setup variant to the bpmn process filter documentation', () => {
  const node = shallow(<ExportFilterHint variant="reportSetup" />);

  expect(node.find(DocsLink).prop('location')).toBe(esPage + '#bpmn-process-filters');
  expect(node.find('span').text()).toContain('processes and variables');
});

it('should link to the opensearch exporter docs when optimize runs on opensearch', () => {
  (useUiConfig as jest.Mock).mockReturnValueOnce({optimizeDatabase: 'opensearch'});

  const node = shallow(<ExportFilterHint variant="variable" />);

  expect(node.find(DocsLink).prop('location')).toBe(osPage + '#variable-name-filters');
});
