/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {ReportTemplateModal, KpiCreationModal, EntityList} from 'components';
import {loadEntities} from 'services';

import {Home} from './Home';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadEntities: jest.fn().mockReturnValue([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      description: 'This is a description',
      data: {
        roleCounts: {},
        subEntityCounts: {},
      },
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb, _err, final) => {
    cb(data);
    final?.();
  }),
  user: {name: 'John Doe', authorizations: ['entity_editor']},
};

beforeEach(() => {
  loadEntities.mockClear();
});

it('should load entities', () => {
  shallow(<Home {...props} />);

  runAllEffects();

  expect(loadEntities).toHaveBeenCalled();
});

it('should show a ReportTemplateModal', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  node.find('EntityList').prop('action').props.create('report');

  expect(node.find(ReportTemplateModal)).toExist();
});

it('should show kpiCreationModal', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  node.find('EntityList').prop('action').props.create('kpi');

  expect(node.find(KpiCreationModal)).toExist();
});

it('should load collection entities with sort parameters', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadEntities).toHaveBeenCalledWith('lastModifier', 'desc');
});

it('should pass loading state to entitylist', async () => {
  const node = shallow(
    <Home
      {...props}
      user={{name: 'John Doe', authorizations: []}}
      mightFail={async (data, cb, _err, final) => {
        cb(await data);
        final();
      }}
    />
  );

  runAllEffects();

  expect(node.find('EntityList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('EntityList').prop('isLoading')).toBe(false);
});

it('should not pass empty state component if user is not an editor', async () => {
  loadEntities.mockReturnValueOnce([]);
  const node = shallow(<Home {...props} user={{name: 'John Doe', authorizations: []}} />);

  runAllEffects();

  await flushPromises();

  expect(node.find(EntityList).prop('emptyStateComponent')).toBe(false);
});

it('should hide edit options for read only users', () => {
  loadEntities.mockReturnValueOnce([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'viewer',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      data: {subEntityCounts: {}},
    },
  ]);
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('rows')[0].actions.length).toBe(0);
});

it('should hide edit options for collection editors', () => {
  loadEntities.mockReturnValueOnce([
    {
      id: '1',
      entityType: 'collection',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test collection',
      data: {subEntityCounts: {}},
    },
  ]);
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('rows')[0].actions.length).toBe(0);
});

it('should hide bulk actions for read only users', () => {
  const node = shallow(<Home {...props} user={{name: 'John Doe', authorizations: []}} />);

  runAllEffects();

  expect(node.find('EntityList').prop('bulkActions')).toBe(false);
});

it('should hide entity creation button for read only users', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  const actionButton = shallow(node.find('EntityList').prop('action'));
  expect(actionButton.find('.CreateNewButton')).toExist();

  node.setProps({user: {name: 'John Doe', authorizations: []}});

  expect(node.find('EntityList').prop('action')).toBe(false);
});

describe('export authorizations', () => {
  it('should show export option for editable entities', () => {
    loadEntities.mockReturnValueOnce([
      {
        entityType: 'dashboard',
        currentUserRole: 'editor',
        lastModified: '2019-11-18T12:29:37+0000',
        data: {subEntityCounts: {}},
      },
    ]);

    const node = shallow(<Home {...props} />);

    runAllEffects();

    expect(
      node
        .find('EntityList')
        .prop('rows')[0]
        .actions.find(({text}) => text === 'Export')
    ).not.toBe(undefined);
  });

  it('should hide export option for collection entities', () => {
    loadEntities.mockReturnValueOnce([
      {
        entityType: 'collection',
        currentUserRole: 'editor',
        lastModified: '2019-11-18T12:29:37+0000',
        data: {subEntityCounts: {}},
      },
    ]);

    const node = shallow(<Home {...props} />);

    runAllEffects();

    expect(
      node
        .find('EntityList')
        .prop('rows')[0]
        .actions.find(({text}) => text === 'Export')
    ).toBe(undefined);
  });

  it('should hide export option for view only entities', () => {
    loadEntities.mockReturnValueOnce([
      {
        entityType: 'report',
        currentUserRole: 'viewer',
        lastModified: '2019-11-18T12:29:37+0000',
        data: {subEntityCounts: {}},
      },
    ]);

    const node = shallow(<Home {...props} />);

    runAllEffects();

    expect(
      node
        .find('EntityList')
        .prop('rows')[0]
        .actions.find(({text}) => text === 'Export')
    ).toBe(undefined);
  });
});

it('should show entity name and description', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('rows')[0].name).toBe('Test Report');
  expect(node.find('EntityList').prop('rows')[0].meta[0]).toBe('This is a description');
});
