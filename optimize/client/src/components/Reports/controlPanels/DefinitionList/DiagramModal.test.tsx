/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {BPMNDiagram} from 'components';

import DiagramModal from './DiagramModal';

const props = {
  open: true,
  xml: '<diagram XML>',
  definitionName: 'definition',
};

it('should render BPMN diagram', () => {
  const node = shallow(<DiagramModal {...props} />);
  runAllEffects();

  expect(node.find(BPMNDiagram).prop('xml')).toBe('<diagram XML>');
});
