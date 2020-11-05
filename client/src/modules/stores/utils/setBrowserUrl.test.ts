/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {setBrowserUrl} from './setBrowserUrl';

describe('setBrowserUrl', () => {
  const historyMock = {push: jest.fn()};
  const locationMock = {pathname: '/instances'};

  const groupedWorkflows = {
    bigVarProcess: {
      bpmnProcessId: 'bigVarProcess',
      name: 'Big variable process',
      workflows: [
        {
          id: '2251799813686288',
          name: 'Big variable process',
          version: 1,
          bpmnProcessId: 'bigVarProcess',
        },
      ],
    },
  };
  const groupedWorkflowsWithoutName = {
    bigVarProcess: {
      bpmnProcessId: 'bigVarProcess',
      workflows: [
        {
          id: '2251799813686288',
          version: 1,
          bpmnProcessId: 'bigVarProcess',
        },
      ],
    },
  };

  it('should set browser url', () => {
    setBrowserUrl(
      historyMock,
      locationMock,
      {workflow: 'bigVarProcess'},
      groupedWorkflows,
      'workflowName'
    );
    expect(historyMock.push).toHaveBeenNthCalledWith(1, {
      pathname: '/instances',
      search: '?filter={"workflow":"bigVarProcess"}&name="workflowName"',
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
    setBrowserUrl(
      historyMock,
      locationMock,
      {workflow: 'bigVarProcess'},
      groupedWorkflows
    );
    expect(historyMock.push).toHaveBeenNthCalledWith(2, {
      pathname: '/instances',
      search:
        '?filter={"workflow":"bigVarProcess"}&name="Big variable process"',
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
    setBrowserUrl(
      historyMock,
      locationMock,
      {workflow: 'bigVarProcess'},
      groupedWorkflowsWithoutName
    );
    expect(historyMock.push).toHaveBeenNthCalledWith(3, {
      pathname: '/instances',
      search: '?filter={"workflow":"bigVarProcess"}&name="bigVarProcess"',
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
    setBrowserUrl(historyMock, locationMock, {}, groupedWorkflows);
    expect(historyMock.push).toHaveBeenNthCalledWith(4, {
      pathname: '/instances',
      search: '?filter={}',
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
    setBrowserUrl(
      historyMock,
      locationMock,
      {active: true, incidents: true, variable: {name: '', value: ''}},
      groupedWorkflows
    );
    expect(historyMock.push).toHaveBeenNthCalledWith(5, {
      pathname: '/instances',
      search: '?filter={"active":true,"incidents":true}',
    });

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
    setBrowserUrl(
      historyMock,
      locationMock,
      {active: true, incidents: true, variable: {name: 'test', value: '123'}},
      groupedWorkflows
    );
    expect(historyMock.push).toHaveBeenNthCalledWith(6, {
      pathname: '/instances',
      search:
        '?filter={"active":true,"incidents":true,"variable":{"name":"test","value":"123"}}',
    });
  });
});
