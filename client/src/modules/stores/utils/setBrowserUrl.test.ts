/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {setBrowserUrl} from './setBrowserUrl';
import {createMemoryHistory} from 'history';

describe('setBrowserUrl', () => {
  let historyMock = createMemoryHistory();
  historyMock.push = jest.fn();

  const locationMock = {
    search: '',
    pathname: '/instances',
    hash: '',
    state: null,
  };

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
    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {workflow: 'bigVarProcess'},
      groupedWorkflows,
      name: 'workflowName',
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(1, {
      pathname: '/instances',
      search:
        'filter=%7B%22workflow%22%3A%22bigVarProcess%22%7D&name=%22workflowName%22',
    });

    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {workflow: 'bigVarProcess'},
      groupedWorkflows,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(2, {
      pathname: '/instances',
      search:
        'filter=%7B%22workflow%22%3A%22bigVarProcess%22%7D&name=%22Big+variable+process%22',
    });

    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {workflow: 'bigVarProcess'},
      groupedWorkflows: groupedWorkflowsWithoutName,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(3, {
      pathname: '/instances',
      search:
        'filter=%7B%22workflow%22%3A%22bigVarProcess%22%7D&name=%22bigVarProcess%22',
    });

    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {},
      groupedWorkflows,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(4, {
      pathname: '/instances',
      search: 'filter=%7B%7D',
    });

    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {active: true, incidents: true, variable: {name: '', value: ''}},
      groupedWorkflows,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(5, {
      pathname: '/instances',
      search: 'filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%7D',
    });

    setBrowserUrl({
      history: historyMock,
      location: locationMock,
      filter: {
        active: true,
        incidents: true,
        variable: {name: 'test', value: '123'},
      },
      groupedWorkflows,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(6, {
      pathname: '/instances',
      search:
        'filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%2C%22variable%22%3A%7B%22name%22%3A%22test%22%2C%22value%22%3A%22123%22%7D%7D',
    });

    setBrowserUrl({
      history: historyMock,
      location: {
        ...locationMock,
        search: '?gseUrl=https://www.testUrl.com&someOtherParam=123',
      },
      filter: {
        active: true,
        incidents: true,
        variable: {name: 'test', value: '123'},
      },
      groupedWorkflows,
    });

    expect(historyMock.push).toHaveBeenNthCalledWith(7, {
      pathname: '/instances',
      search:
        'filter=%7B%22active%22%3Atrue%2C%22incidents%22%3Atrue%2C%22variable%22%3A%7B%22name%22%3A%22test%22%2C%22value%22%3A%22123%22%7D%7D&gseUrl=https%3A%2F%2Fwww.testUrl.com',
    });
  });
});
