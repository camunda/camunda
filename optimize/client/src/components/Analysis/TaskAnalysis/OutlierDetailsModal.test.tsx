/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {AnalysisDurationChartEntry} from 'types';

import VariablesTable from './VariablesTable';
import OutlierDetailsModal from './OutlierDetailsModal';
import {OutlierNode, AnalysisProcessDefinitionParameters} from './service';

const selectedOutlierNode = {
  name: 'test',
  higherOutlier: {
    count: 4,
    relation: 1.1,
  },
  data: [
    {key: 1, value: 1, outlier: false},
    {key: 2, value: 2, outlier: true},
  ] as AnalysisDurationChartEntry[],
  totalCount: 123,
} as OutlierNode;

const props = {
  selectedOutlierNode,
  onClose: jest.fn(),
  config: {} as AnalysisProcessDefinitionParameters,
};

it('should pass outlier data to DurationChart and VariablesTable', () => {
  const node = shallow(<OutlierDetailsModal {...props} />);

  expect(node.find('DurationChart').prop('data')).toEqual(selectedOutlierNode.data);
  expect(node.find('DurationChart').prop('colors')).toEqual(['#eeeeee', '#1991c8']);
  expect(node.find(VariablesTable).prop('selectedOutlierNode')).toEqual(selectedOutlierNode);
});
