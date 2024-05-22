/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {DMNDiagram} from 'components';

import {DecisionTable} from './DecisionTable';
import createHitsColumnAddon from './HitsColumnAddon';

jest.mock('./HitsColumnAddon');

createHitsColumnAddon.mockReturnValue({
  entryPoints: {
    rules: {
      a: document.createElement('td'),
      b: document.createElement('td'),
    },
    summary: document.createElement('td'),
  },
  Addon: 'addon',
});

const props = {
  report: {
    data: {
      configuration: {
        xml: 'dmn xml string',
      },
      definitions: [
        {
          key: 'key',
        },
      ],
    },
    result: {
      instanceCount: 3,
      data: [
        {key: 'a', value: 1},
        {key: 'b', value: 2},
      ],
    },
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should render content in DmnJsPortals', () => {
  const node = shallow(<DecisionTable {...props} />);

  node.find(DMNDiagram).prop('onLoad')();

  expect(node).toMatchSnapshot();
});

it('should display meaningful data if there are no evaluations', () => {
  const node = shallow(
    <DecisionTable
      {...props}
      report={{data: props.report.data, result: {instanceCount: 0, data: []}}}
    />
  );

  node.find(DMNDiagram).prop('onLoad')();

  expect(node).toMatchSnapshot();
});
