/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstanceV2';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {Drd} from '.';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<Drd />', () => {
  beforeEach(() => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification],
      page: {totalItems: 1},
    });
  });

  it('should render DRD', async () => {
    render(
      <Drd
        decisionEvaluationInstanceKey={
          invoiceClassification.decisionEvaluationInstanceKey
        }
        decisionEvaluationKey={invoiceClassification.decisionEvaluationKey}
        decisionDefinitionKey={invoiceClassification.decisionDefinitionKey}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Default View mock')).toBeInTheDocument();
    expect(
      await screen.findByText('Definitions Name Mock'),
    ).toBeInTheDocument();
  });
});
