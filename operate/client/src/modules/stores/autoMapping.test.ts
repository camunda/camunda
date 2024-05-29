/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {autoMappingStore} from './autoMapping';
import {waitFor} from '@testing-library/react';

describe('autoMappingStore', () => {
  afterEach(() => {
    autoMappingStore.reset();
  });

  it('should provide auto mapped flow nodes', async () => {
    // Fetch migration source diagram
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));
    processXmlMigrationSourceStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationSourceStore.state.status).toBe('fetched'),
    );

    // Fetch migration target diagram
    mockFetchProcessXML().withSuccess(open('orderProcess_v2.bpmn'));
    processXmlMigrationTargetStore.fetchProcessXml();
    await waitFor(() =>
      expect(processXmlMigrationTargetStore.state.status).toBe('fetched'),
    );

    const {isAutoMappable, autoMappableFlowNodes} = autoMappingStore;

    /**
     * orderProcess.bpmn contains:
     * - checkPayment (service task)
     * - requestForPayment (service task)
     * - shipArticles (user task)
     *
     * orderProcess_v2.bpmn contains:
     * - checkPayment (service task)
     * - requestForPayment (service task)
     * - shipArticles (service task)
     * - notifyCustomer (service task)
     *
     * Expect only checkPayment and requestForPayment to be mappable,
     * because they are the only ones with the same id and type.
     */
    expect(autoMappableFlowNodes).toEqual([
      {
        id: 'checkPayment',
        type: 'bpmn:ServiceTask',
      },
      {
        id: 'requestForPayment',
        type: 'bpmn:ServiceTask',
      },
    ]);

    expect(isAutoMappable('checkPayment')).toBe(true);
    expect(isAutoMappable('requestForPayment')).toBe(true);

    expect(isAutoMappable('shipArticles')).toBe(false);
    expect(isAutoMappable('notifyCustomer')).toBe(false);
    expect(isAutoMappable('unknownFlowNodeId')).toBe(false);
    expect(isAutoMappable('')).toBe(false);
  });

  it('should toggle mapped filter', () => {
    expect(autoMappingStore.state.isMappedFilterEnabled).toBe(false);

    autoMappingStore.toggleMappedFilter();
    expect(autoMappingStore.state.isMappedFilterEnabled).toBe(true);

    autoMappingStore.toggleMappedFilter();
    expect(autoMappingStore.state.isMappedFilterEnabled).toBe(false);

    autoMappingStore.toggleMappedFilter();
    expect(autoMappingStore.state.isMappedFilterEnabled).toBe(true);

    autoMappingStore.reset();
    expect(autoMappingStore.state.isMappedFilterEnabled).toBe(false);
  });
});
