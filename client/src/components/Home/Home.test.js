/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportTemplateModal from './modals/ReportTemplateModal';

import {Home} from './Home';
import {loadEntities} from './service';

jest.mock('./service', () => ({
  loadEntities: jest.fn().mockReturnValue([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      data: {
        roleCounts: {},
        subEntityCounts: {},
      },
      reportType: 'process',
      combined: false,
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  user: {name: 'John Doe', authorizations: []},
};

beforeEach(() => {
  loadEntities.mockClear();
});

it('should load entities', () => {
  shallow(<Home {...props} />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should display the user name', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.welcomeMessage')).toIncludeText('John Doe');
});

it('should show a ReportTemplateModal', () => {
  const node = shallow(<Home {...props} />);

  node.find('EntityList').prop('action')().props.createProcessReport();

  expect(node.find(ReportTemplateModal)).toExist();
});

it('should load collection entities with sort parameters', () => {
  const node = shallow(<Home {...props} />);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadEntities).toHaveBeenCalledWith('lastModifier', 'desc');
});

it('should set the loading state of the entity list', async () => {
  const node = shallow(<Home {...props} mightFail={async (data, cb) => cb(await data)} />);

  expect(node.find('EntityList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('EntityList').prop('isLoading')).toBe(false);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(node.find('EntityList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('EntityList').prop('isLoading')).toBe(false);
});

it('should include an option to export reports for superusers', () => {
  const node = shallow(<Home {...props} />);

  expect(
    node
      .find('EntityList')
      .prop('data')[0]
      .actions.find(({text}) => text === 'Export')
  ).toBe(undefined);

  node.setProps({user: {name: 'John Doe', authorizations: ['import_export']}});

  expect(
    node
      .find('EntityList')
      .prop('data')[0]
      .actions.find(({text}) => text === 'Export')
  ).not.toBe(undefined);
});
