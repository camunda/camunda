/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Home from './Home';
import {loadEntities} from './service';

jest.mock('./service', () => ({
  loadEntities: jest.fn().mockReturnValue([
    {
      id: 'aCollectionId',
      name: 'aCollectionName',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      lastModifier: 'user_id',
      entityType: 'collection',
      data: {
        subEntityCounts: {
          dashboard: 2,
          report: 8
        },
        roleCounts: {
          user: 5,
          group: 2
        }
      }
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      lastModifier: 'user_id',
      entityType: 'dashboard',
      data: {
        subEntityCounts: {
          report: 8
        },
        roleCounts: {}
      }
    },
    {
      id: 'aReportId',
      name: 'aReport',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      data: {subEntityCounts: {}, roleCounts: {}},
      lastModifier: 'user_id',
      reportType: 'process', // or 'decision'
      combined: false,
      entityType: 'report'
    }
  ])
}));

it('should load entities', async () => {
  await shallow(<Home />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should match snapshot', async () => {
  const node = await shallow(<Home />);

  expect(node).toMatchSnapshot();
});
