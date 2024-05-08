/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {processXmlStore} from './processXml.list';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('stores/processXml/processXml.list', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should get flowNodeFilterOptions', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXml);

    processXmlStore.fetchProcessXml('1');

    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(processXmlStore.flowNodeFilterOptions).toEqual([
      {label: 'EndEvent_0crvjrk', value: 'EndEvent_0crvjrk'},
      {label: 'StartEvent_1', value: 'StartEvent_1'},
      {label: 'userTask', value: 'userTask'},
    ]);
  });
});
