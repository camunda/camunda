/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {ProcessXmlBase} from './processXml.base';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

const processXmlStore = new ProcessXmlBase();

describe('stores/processXml/processXml.list', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should fetch xml', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXml);

    expect(processXmlStore.state.status).toBe('initial');

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(processXmlStore.state.xml).toBe(mockProcessXml);
  });

  it('should handle errors', async () => {
    mockFetchProcessXML().withServerError();

    processXmlStore.fetchProcessXml('1');

    await waitFor(() => expect(processXmlStore.state.status).toBe('error'));
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessXML().withNetworkError();

    await processXmlStore.fetchProcessXml('1');

    await waitFor(() => expect(processXmlStore.state.xml).toEqual(null));

    mockFetchProcessXML().withSuccess(mockProcessXml);

    eventListeners.online();

    await waitFor(() =>
      expect(processXmlStore.state.xml).toEqual(mockProcessXml),
    );

    consoleErrorMock.mockRestore();
    window.addEventListener = originalEventListener;
  });
});
