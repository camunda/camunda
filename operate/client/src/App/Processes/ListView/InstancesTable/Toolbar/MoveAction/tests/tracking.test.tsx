/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';
import {fetchProcessInstances, fetchProcessXml, getWrapper} from '../../mocks';
import {MoveAction} from '..';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';
import {tracking} from 'modules/tracking';

const PROCESS_ID = 'MoveModificationProcess';
const mockProcessXML = open('MoveModificationProcess.bpmn');

describe('<MoveAction /> - tracking', () => {
  it('should track move button click', async () => {
    const trackSpy = jest.spyOn(tracking, 'track');

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-move-modification-move-button-clicked',
    });
  });
});
