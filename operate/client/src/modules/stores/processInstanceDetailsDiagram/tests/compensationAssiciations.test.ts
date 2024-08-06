/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsDiagramStore} from '..';
import {processInstanceDetailsStore} from '../../processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';

describe('hasCalledProcessInstances', () => {
  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should get compensation associations', async () => {
    // CompensationProcess.bpmn contains 3 associations. Two of them
    // are related to the compensation event and should be returned.
    mockFetchProcessXML().withSuccess(open('CompensationProcess.bpmn'));

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'COMPLETED',
        processId: '10',
      }),
    );

    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual(
        'fetched',
      ),
    );

    expect(
      processInstanceDetailsDiagramStore.compensationAssociations,
    ).toHaveLength(2);
    expect(
      processInstanceDetailsDiagramStore.compensationAssociations[0],
    ).toMatchObject({id: 'Association_2'});
    expect(
      processInstanceDetailsDiagramStore.compensationAssociations[1],
    ).toMatchObject({id: 'Association_1'});
  });
});
