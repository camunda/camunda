/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getEntitiesCollections, toggleEntityCollection} from './collectionService';
import {updateEntity} from './entityService';
jest.mock('./entityService', () => ({
  updateEntity: jest.fn()
}));

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [
      {
        id: 'reportID1'
      },
      {
        id: 'reportID2'
      }
    ]
  }
};

const processReport = {
  id: 'reportID3',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

it('should return entities Collections as a hash of arrays', () => {
  expect(getEntitiesCollections([collection])).toEqual({
    reportID1: [collection],
    reportID2: [collection]
  });
});

it('should call passed load data function', async () => {
  const spy = jest.fn();
  await toggleEntityCollection(spy)(processReport, collection, false);
  expect(spy).toHaveBeenCalled();
});

it('should correctly add report to a collection', async () => {
  await toggleEntityCollection(jest.fn())(processReport, collection, false);
  expect(updateEntity).toHaveBeenCalledWith('collection', 'aCollectionId', {
    data: {entities: ['reportID1', 'reportID2', 'reportID3']}
  });
});

it('should correctly remove report to a collection', async () => {
  await toggleEntityCollection(jest.fn())(processReport, collection, true);
  expect(updateEntity).toHaveBeenCalledWith('collection', 'aCollectionId', {
    data: {entities: ['reportID1', 'reportID2']}
  });
});
