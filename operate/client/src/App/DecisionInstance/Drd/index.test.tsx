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
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {drdDataStore} from 'modules/stores/drdData';
import {Drd} from '.';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  drdDataStore.init();
  decisionXmlStore.init();
  decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

  useEffect(() => {
    return () => {
      decisionInstanceDetailsStore.reset();
      decisionXmlStore.reset();
      drdDataStore.reset();
    };
  }, []);
  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('<Drd />', () => {
  beforeEach(() => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
  });

  it('should render DRD', async () => {
    render(<Drd />, {wrapper: Wrapper});

    expect(await screen.findByText('Default View mock')).toBeInTheDocument();
    expect(
      await screen.findByText('Definitions Name Mock'),
    ).toBeInTheDocument();
  });
});
