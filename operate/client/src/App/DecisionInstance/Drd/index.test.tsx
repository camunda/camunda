/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {render, screen} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdDataStore} from 'modules/stores/drdData';
import {Drd} from '.';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  drdDataStore.init();
  decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

  useEffect(() => {
    return () => {
      decisionInstanceDetailsStore.reset();
      drdDataStore.reset();
    };
  }, []);
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<Drd />', () => {
  beforeEach(() => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
  });

  it('should render DRD', async () => {
    render(<Drd decisionDefinitionKey="22123481044261742" />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Default View mock')).toBeInTheDocument();
    expect(
      await screen.findByText('Definitions Name Mock'),
    ).toBeInTheDocument();
  });
});
