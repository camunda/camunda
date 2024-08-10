/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {
  invoiceClassification,
  assignApproverGroup,
  literalExpression,
} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {DecisionPanel} from '.';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    decisionXmlStore.init();
    return () => {
      decisionXmlStore.reset();
      decisionInstanceDetailsStore.reset();
    };
  }, []);
  return <>{children}</>;
};

describe('<DecisionPanel />', () => {
  beforeEach(() => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);
  });

  it('should render decision table', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('DecisionTable view mock'),
    ).toBeInTheDocument();
    expect(screen.queryByText('An error occurred')).not.toBeInTheDocument();
  });

  it('should render literal expression', async () => {
    mockFetchDecisionInstance().withSuccess(literalExpression);

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('LiteralExpression view mock'),
    ).toBeInTheDocument();
  });

  it('should render incident banner', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    decisionInstanceDetailsStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: Wrapper});

    expect(await screen.findByText('An error occurred')).toBeInTheDocument();
  });
});
