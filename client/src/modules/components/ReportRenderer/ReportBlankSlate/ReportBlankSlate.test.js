/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ReportBlankSlate from './ReportBlankSlate';

it('should render without crashing', () => {
  mount(<ReportBlankSlate />);
});

it('should render a message provided as a prop', () => {
  const node = mount(<ReportBlankSlate errorMessage="foo" />);

  expect(node.find('.ReportBlankSlate__message')).toIncludeText('foo');
});

it('should should not render inner illustrations if report type is combined', () => {
  const node = mount(<ReportBlankSlate errorMessage="foo" isCombined />);

  expect(node.find('.ReportBlankSlate__illustrationDropdown').exists()).toBeFalsy();
  expect(node.find('.ReportBlankSlate__diagramIllustrations').exists()).toBeFalsy();
});
