/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
