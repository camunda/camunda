/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter, Route} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData} from './index.setup';
import {mockSequenceFlows, mockEvents} from './TopPanel/index.setup';
import {mockSuccessResponseForActivityTree} from './FlowNodeInstanceLog/index.setup';
import {PAGE_TITLE} from 'modules/constants';

import {getWorkflowName} from 'modules/utils/instance';
import {
  fetchWorkflowInstance,
  fetchWorkflowCoreStatistics,
  fetchVariables,
  fetchSequenceFlows,
} from 'modules/api/instances';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
import {fetchEvents} from 'modules/api/events';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {Instance} from './index';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/diagram');
jest.mock('modules/api/events');
jest.mock('modules/api/instances');
jest.mock('modules/api/activityInstances');

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:id">{children}</Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Instance', () => {
  beforeAll(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValue('');
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchSequenceFlows.mockResolvedValue(mockSequenceFlows);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchEvents.mockResolvedValue(mockEvents);
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValue(
      mockSuccessResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchWorkflowCoreStatistics.mockResolvedValue({
      coreStatistics: {
        running: 821,
        active: 90,
        withIncidents: 731,
      },
    });
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
    fetchVariables.mockResolvedValue([
      {
        id: '2251799813686037-mwst',
        name: 'newVariable',
        value: '1234',
        scopeId: '2251799813686037',
        workflowInstanceId: '2251799813686037',
        hasActiveOperation: false,
      },
    ]);
  });

  afterEach(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockReset' does not exist on type '(id: ... Remove this comment to see the full error message
    fetchWorkflowInstance.mockReset();
  });

  it('should render and set the page title', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowInstance.mockResolvedValueOnce(
      testData.fetch.onPageLoad.workflowInstance
    );

    render(<Instance />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(screen.getByText('newVariable')).toBeInTheDocument();
    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        testData.fetch.onPageLoad.workflowInstance.id,
        getWorkflowName(testData.fetch.onPageLoad.workflowInstance)
      )
    );
  });

  describe('polling', () => {
    it('should poll for active instances until component is unmounted', async () => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
      fetchWorkflowInstance.mockResolvedValue(
        testData.fetch.onPageLoad.workflowInstance
      );

      const component = render(<Instance />, {wrapper: Wrapper});

      jest.useFakeTimers();

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(await screen.findByTestId('diagram')).toBeInTheDocument();
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(5000);
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(2);

      jest.advanceTimersByTime(5000);
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(3);

      component.unmount();
      jest.advanceTimersByTime(5000);
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(3);
      jest.useRealTimers();
    });

    it('should not poll for completed instances', async () => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
      fetchWorkflowInstance.mockResolvedValue(
        testData.fetch.onPageLoad.workflowInstanceCompleted
      );
      render(<Instance />, {wrapper: Wrapper});
      jest.useFakeTimers();

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(await screen.findByTestId('diagram')).toBeInTheDocument();
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(5000);
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(1);
      jest.useRealTimers();
    });

    it('should not poll for canceled instances', async () => {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValue' does not exist on typ... Remove this comment to see the full error message
      fetchWorkflowInstance.mockResolvedValue(
        testData.fetch.onPageLoad.workflowInstanceCanceled
      );

      render(<Instance />, {wrapper: Wrapper});

      jest.useFakeTimers();

      await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
      expect(await screen.findByTestId('diagram')).toBeInTheDocument();
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(5000);
      expect(fetchWorkflowInstance).toHaveBeenCalledTimes(1);
      jest.useRealTimers();
    });
  });
});
