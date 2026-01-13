/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MoveAction} from '..';
import {open} from 'modules/mocks/diagrams';
import {tracking} from 'modules/tracking';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {
  mockProcessInstancesV2,
  setupSelectionStoreWithInstances,
  createWrapper,
} from '../../tests/mocks';

const PROCESS_ID = 'MoveModificationProcess';
const mockProcessXML = open('MoveModificationProcess.bpmn');

describe('<MoveAction /> - tracking', () => {
  it('should track move button click', async () => {
    const trackSpy = vi.spyOn(tracking, 'track');

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockSearchProcessInstances().withSuccess({
      items: mockProcessInstancesV2,
      page: {totalItems: mockProcessInstancesV2.length},
    });

    const {user} = render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    await waitFor(() => {
      const button = screen.getByRole('button', {name: /move/i});
      const title = button.getAttribute('title');
      expect(title).not.toBe(
        'Please select an element from the diagram first.',
      );
    });

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-move-modification-move-button-clicked',
    });
  });
});
