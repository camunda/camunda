/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
