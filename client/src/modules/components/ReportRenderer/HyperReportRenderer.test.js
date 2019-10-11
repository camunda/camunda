/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HyperReportRenderer from './HyperReportRenderer';

it('should convert a hypermap to a combined report', () => {
  const node = shallow(
    <HyperReportRenderer
      report={{
        combined: false,
        reportType: 'process',
        data: {
          processDefinitionKey: 'aKey',
          processDefinitionVersion: '1',
          view: {
            property: 'duration',
            entity: 'userTask'
          },
          groupBy: {
            type: 'assignee'
          },
          visualization: 'bar',
          configuration: {distributedBy: 'userTask'}
        },
        result: {
          data: [
            {
              key: 'Anne',
              label: 'Anne',
              value: [
                {key: 'taskId1', label: 'Usertask 1', value: 8},
                {key: 'taskId2', label: 'Usertask 2', value: 1},
                {key: 'taskId3', label: 'Usertask 3', value: 65}
              ]
            },
            {
              key: 'Bernd',
              label: 'Bernd',
              value: [
                {key: 'taskId1', label: 'Usertask 1', value: 3},
                {key: 'taskId2', label: 'Usertask 2', value: 17},
                {key: 'taskId3', label: 'Usertask 3', value: 22}
              ]
            },
            {
              key: 'Chris',
              label: 'Chris',
              value: [
                {key: 'taskId1', label: 'Usertask 1', value: 1},
                {key: 'taskId2', label: 'Usertask 2', value: 0},
                {key: 'taskId3', label: 'Usertask 3', value: 73}
              ]
            }
          ],
          isComplete: true,
          instanceCount: 1234,
          type: 'hyperMap'
        }
      }}
    />
  );

  expect(node).toMatchSnapshot();
});
