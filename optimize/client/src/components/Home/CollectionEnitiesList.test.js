/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {ReportTemplateModal, KpiCreationModal, DashboardTemplateModal} from 'components';

import CollectionEnitiesList from './CollectionEnitiesList';
import {importEntity} from './service';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useParams: jest.fn().mockReturnValue({1: 'aCollectionId/'}),
}));

jest.mock('./service', () => ({
  checkConflicts: jest.fn(),
  importEntity: jest.fn(),
  removeEntities: jest.fn(),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation((data, cb, _err, final) => {
      cb(data);
      final?.();
    }),
  })),
}));

const entities = [
  {
    id: 'aDashboardId',
    name: 'aDashboard',
    description: 'a description',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    entityType: 'dashboard',
    currentUserRole: 'editor', // or viewer
    data: {
      subEntityCounts: {
        report: 8,
      },
      roleCounts: {},
    },
  },
  {
    id: 'aReportId',
    name: 'aReport',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    reportType: 'process', // or "decision"
    combined: false,
    entityType: 'report',
    data: {
      subEntityCounts: {},
      roleCounts: {},
    },
    currentUserRole: 'editor', // or viewer
  },
];
const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  owner: 'user_id',
  lastModifier: 'user_id',
  currentUserRole: 'editor',
  data: {},
};

const props = {
  entities,
  collection,
  isLoading: false,
  sorting: null,
  copyEntity: jest.fn(),
  deleteEntity: jest.fn(),
  loadEntities: jest.fn(),
  redirectTo: jest.fn(),
};

it('should pass entity to on copy', () => {
  const copyEntitySpy = jest.fn();
  const node = shallow(<CollectionEnitiesList {...props} copyEntity={copyEntitySpy} />);

  node.find('EntityList').prop('rows')[0].actions[1].action();

  expect(copyEntitySpy).toBeCalledWith(entities[0]);
});

it('should pass entity to on delete', () => {
  const deleteEntitySpy = jest.fn();
  const node = shallow(<CollectionEnitiesList {...props} deleteEntity={deleteEntitySpy} />);

  node.find('EntityList').prop('rows')[0].actions[2].action();

  expect(deleteEntitySpy).toBeCalledWith(entities[0]);
});

it('should call importEntity with correct id when the import button is clicked', () => {
  // Given
  const mockFileContent = 'mock file content';
  const readAsTextSpy = jest.fn();
  const addEventListenerSpy = jest.fn();
  const fileReaderMock = {
    addEventListener: addEventListenerSpy,
    readAsText: readAsTextSpy,
    result: null,
  };

  //  When: simulate file input change
  global.FileReader = jest.fn(() => fileReaderMock);

  const node = shallow(<CollectionEnitiesList {...props} />);

  node.find('input').simulate('change');
  fileReaderMock.result = mockFileContent;

  // Then
  expect(readAsTextSpy).toHaveBeenCalled();

  // When: emulate the 'load' event
  const loadListener = addEventListenerSpy.mock.calls[0][1];
  fileReaderMock.result = mockFileContent;
  loadListener();

  // Then
  expect(importEntity).toHaveBeenCalledWith(mockFileContent, 'aCollectionId');
});

it('should redirect to the edit page when the edit button is clicked', () => {
  const redirectToSpy = jest.fn();
  const node = shallow(<CollectionEnitiesList {...props} redirectTo={redirectToSpy} />);
  node.find('EntityList').prop('rows')[0].actions[0].action();

  expect(redirectToSpy).toBeCalledWith('dashboard/aDashboardId/edit');
});

it('should open DashboardTemplateModal when the create new button is clicked', () => {
  const openCreationModalSpy = jest.fn();
  const node = shallow(
    <CollectionEnitiesList {...props} openCreationModal={openCreationModalSpy} />
  );
  const emptyState = node.find('EntityList').prop('emptyStateComponent');
  shallow(emptyState).find('CreateNewButton').prop('create')('dashboard');

  expect(node.find(DashboardTemplateModal)).toExist();
});

it('should open ReportTemplateModal when the create new button is clicked', () => {
  const openCreationModalSpy = jest.fn();
  const node = shallow(
    <CollectionEnitiesList {...props} openCreationModal={openCreationModalSpy} />
  );
  const emptyState = node.find('EntityList').prop('emptyStateComponent');
  shallow(emptyState).find('CreateNewButton').prop('create')('report');

  expect(node.find(ReportTemplateModal)).toExist();
});

it('should open KpiCreationModal when the create new button is clicked', () => {
  const openCreationModalSpy = jest.fn();
  const node = shallow(
    <CollectionEnitiesList {...props} openCreationModal={openCreationModalSpy} />
  );
  const emptyState = node.find('EntityList').prop('emptyStateComponent');
  shallow(emptyState).find('CreateNewButton').prop('create')('kpi');

  expect(node.find(KpiCreationModal)).toExist();
});

it('should show entity name and description', () => {
  const node = shallow(<CollectionEnitiesList {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('rows')[0].name).toBe('aDashboard');
  expect(node.find('EntityList').prop('rows')[0].meta[0]).toBe('a description');
});

it('should include an option to export reports for entity editors', () => {
  const node = shallow(<CollectionEnitiesList {...props} />);

  expect(
    node
      .find('EntityList')
      .prop('rows')[1]
      .actions.find(({text}) => text === 'Export')
  ).not.toBe(undefined);
});

it('should hide the export option for entity viewers', () => {
  const entities = [
    {
      entityType: 'report',
      currentUserRole: 'viewer',
      lastModified: '2019-11-18T12:29:37+0000',
      data: {subEntityCounts: {}},
    },
  ];
  const node = shallow(<CollectionEnitiesList {...props} entities={entities} />);

  expect(
    node
      .find('EntityList')
      .prop('rows')[0]
      .actions.find(({text}) => text === 'Export')
  ).toBe(undefined);
});

it('should not show actions in empty state if user is not an editor', async () => {
  const collection = {
    id: 'aCollectionId',
    name: 'aCollectionName',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'viewer',
    data: {},
  };

  const node = shallow(<CollectionEnitiesList {...props} collection={collection} />);

  const emptyState = node.find('EntityList').prop('emptyStateComponent');

  expect(emptyState.props.title).toBe('There are no items created yet');
  expect(emptyState.props.actions).toBeUndefined();
});

it('should hide create new button if the user role is viewer', () => {
  const collection = {
    id: 'aCollectionId',
    name: 'aCollectionName',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'viewer',
    data: {},
  };
  const node = shallow(<CollectionEnitiesList {...props} collection={collection} />);

  expect(node.find('EntityList').prop('action')).toBe(false);
});
