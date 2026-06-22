/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {DocsLink} from 'components/DocsLink';

import ExportFilterHint from './ExportFilterHint';

const EXPORTER_DOCS_PAGE =
  'self-managed/components/orchestration-cluster/zeebe/exporters/elasticsearch-exporter/';

it('should render an info toggletip with the localized hint text and icon label', () => {
  const node = shallow(<ExportFilterHint variant="variable" />);

  expect(node.find('Toggletip')).toExist();
  expect(node.find('ToggletipButton').prop('label')).toContain('export filtering');
  expect(node.find('span').text()).toContain('exporter configuration');
});

it('should link the variable variant to the variable-name filter documentation', () => {
  const node = shallow(<ExportFilterHint variant="variable" />);

  expect(node.find(DocsLink).prop('location')).toBe(EXPORTER_DOCS_PAGE + '#variable-name-filters');
});

it('should link the report-setup variant to the bpmn process filter documentation', () => {
  const node = shallow(<ExportFilterHint variant="reportSetup" />);

  expect(node.find(DocsLink).prop('location')).toBe(EXPORTER_DOCS_PAGE + '#bpmn-process-filters');
  expect(node.find('span').text()).toContain('processes and variables');
});
