/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import OutlierControlPanel from './OutlierControlPanel';

const data = {
  processDefinitionKey: 'aKey',
  processDefinitionVersions: ['aVersion'],
  tenantIds: [],
  filters: [],
  xml: 'aFooXml',
};

it('show display a definition selection and an info message', () => {
  const node = shallow(<OutlierControlPanel {...data} />);

  expect(node.find('ControlPanel').dive()).toIncludeText(
    'Heatmap displays the incidence of higher outliers based on duration'
  );
});
