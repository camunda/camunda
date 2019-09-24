/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

import {loadReports, loadReportsInCollection} from './service';

jest.mock('request', () => ({get: jest.fn()}));

it('should only return reports, not dashboards', async () => {
  get.mockReturnValueOnce({
    json: () => [
      // sorted primarily by type (collection < dashboard < report) & secondarily by lastModified for the same type
      {
        id: 'aCollectionId',
        name: 'aCollectionName',
        lastModified: '2017-11-11T11:11:11.1111+0200',
        created: '2017-11-11T11:11:11.1111+0200',
        owner: 'user_id',
        lastModifier: 'user_id',
        entityType: 'collection',
        currentUserRole: 'manager', // or editor, viewer
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
        lastModified: '2017-11-11T11:11:11.1111+0200',
        created: '2017-11-11T11:11:11.1111+0200',
        owner: 'user_id',
        lastModifier: 'user_id',
        entityType: 'dashboard',
        currentUserRole: 'editor', // or viewer
        data: {
          subEntityCounts: {
            report: 8
          }
        }
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
        currentUserRole: 'editor' // or viewer
      }
    ]
  });

  const result = await loadReports();

  expect(get).toHaveBeenCalled();
  expect(result).toMatchSnapshot();
});

it('should load reports from a collection', async () => {
  get.mockReturnValueOnce({
    json: () => ({
      id: 'aCollectionId',
      name: 'aCollectionName',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      created: '2017-11-11T11:11:11.1111+0200',
      owner: 'user_id',
      lastModifier: 'user_id',
      currentUserRole: 'manager', // or editor, viewer
      data: {
        configuration: {},
        entities: [
          {
            id: 'aDashboardId',
            name: 'aDashboard',
            lastModified: '2017-11-11T11:11:11.1111+0200',
            created: '2017-11-11T11:11:11.1111+0200',
            owner: 'user_id',
            lastModifier: 'user_id',
            entityType: 'dashboard',
            currentUserRole: 'editor', // or viewer
            data: {
              subEntityCounts: {
                report: 8
              }
            }
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
            currentUserRole: 'editor' // or viewer
          }
        ],
        roles: [], // array of role objects, for details see role endpoints
        scope: [] // array of scope objects, for details see scope endpoints
      }
    })
  });

  const result = await loadReportsInCollection('123');

  expect(get).toHaveBeenCalledWith('api/collection/123');
  expect(result).toMatchSnapshot();
});
