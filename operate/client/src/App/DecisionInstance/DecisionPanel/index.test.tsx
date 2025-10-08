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
import {DecisionPanel} from '.';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';

vi.mock('modules/components/DecisionViewer', () => ({
  DecisionViewer: vi.fn(
    ({decisionViewId}: {decisionViewId: string | undefined}) => {
      if (decisionViewId === 'invoiceClassification') {
        return <div>DecisionTable view mock</div>;
      } else if (decisionViewId === 'calc-key-figures') {
        return <div>LiteralExpression view mock</div>;
      } else {
        return <div>Default View mock</div>;
      }
    },
  ),
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('<DecisionPanel />', () => {
  beforeEach(() => {
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
  });

  it('should render decision table', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(<DecisionPanel decisionEvaluationInstanceKey="337423841237089" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('DecisionTable view mock'),
    ).toBeInTheDocument();
    expect(screen.queryByText('An error occurred')).not.toBeInTheDocument();
  });

  it('should render literal expression', async () => {
    mockFetchDecisionInstance().withSuccess(literalExpression);

    render(<DecisionPanel decisionEvaluationInstanceKey="337423841237089" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('LiteralExpression view mock'),
    ).toBeInTheDocument();
  });

  it('should render incident banner', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    render(<DecisionPanel decisionEvaluationInstanceKey="337423841237089" />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('An error occurred')).toBeInTheDocument();
  });

  it('should show permission error when decision definition access is forbidden', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockFetchDecisionDefinitionXML().withServerError(403);

    render(<DecisionPanel decisionEvaluationInstanceKey="337423841237089" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('Missing permissions to view the Definition'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Please contact your organization owner or admin to give you the necessary permissions to read this definition',
      ),
    ).toBeInTheDocument();
  });
});
