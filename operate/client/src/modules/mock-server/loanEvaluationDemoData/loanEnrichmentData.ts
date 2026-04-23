/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentElementData} from '../agentDemoData/agentEnrichmentData';

export const MOCK_LOAN_ENRICHMENT_DATA: Record<string, AgentElementData> = {
  '5551799813685010': {
    status: 'COMPLETED',
    modelProvider: 'Anthropic',
    modelId: 'claude-sonnet-4-6',
    systemPrompt: `You are a **Risk Assessment Agent** for a financial services company. You evaluate loan applications by checking credit scores, verifying income, assessing risk profiles, and checking fraud databases. Use the provided tools to gather all necessary data before making a recommendation.

Wrap minimal, inspectable reasoning in *exactly* this XML template:

<thinking>
  <context>...briefly state what you know and what you still need...</context>
  <reflection>...list candidate tools, justify which you will call next and why...</reflection>
</thinking>`,
    userPrompt:
      'Evaluate loan application APP-2026-4821 for applicant Maria Santos (ID: 78432). Requested amount: €250,000. Purpose: Home purchase. Term: 360 months.',
    summary:
      'Evaluated loan application for Maria Santos. Checked credit score (742), verified income (€85,000/year), assessed risk profile (moderate), and ran fraud check (clear). Recommended approval with standard terms. Notified lending team via Slack.',
    iterations: [
      {
        iterationNumber: 1,
        startTimestamp: '2026-04-10T09:15:00.200Z',
        endTimestamp: '2026-04-10T09:15:02.800Z',
        finishReason: 'TOOL_EXECUTION',
        messageId: 'msg_loan_01',
        userMessage:
          'Evaluate loan application APP-2026-4821 for applicant Maria Santos (ID: 78432). Requested amount: €250,000. Purpose: Home purchase. Term: 360 months.',
        reasoning:
          "I need to evaluate this loan application. First, I should check the applicant's credit score and verify their income in parallel, since these are independent checks.",
        agentMessage:
          "Starting loan evaluation for Maria Santos. I'll check the credit score and verify income first.",
        toolCalls: [
          {
            toolName: 'Get credit score',
            toolElementId: 'get_credit_score',
            toolDescription:
              'Retrieves the credit score for a given applicant from the credit bureau',
            rationale:
              "Need to check the applicant's creditworthiness before assessing the loan.",
            input: {applicantId: '78432'},
            output: {
              applicantId: '78432',
              score: 742,
              rating: 'Good',
              reportDate: '2026-04-10',
              delinquencies: 0,
              totalAccounts: 12,
            },
            status: 'COMPLETED',
            duration: '1.2s',
          },
          {
            toolName: 'Verify income',
            toolElementId: 'verify_income',
            toolDescription:
              "Verifies the applicant's income against employer and tax records",
            rationale:
              'Need to confirm income to assess debt-to-income ratio for the requested loan amount.',
            input: {applicantId: '78432'},
            output: {
              applicantId: '78432',
              annualIncome: 85000,
              currency: 'EUR',
              employer: 'TechCorp GmbH',
              employmentStatus: 'Full-time',
              verified: true,
            },
            status: 'COMPLETED',
            duration: '1.8s',
          },
        ],
        tokenUsage: {input: 520, output: 95, reasoning: 42},
      },
      {
        iterationNumber: 2,
        startTimestamp: '2026-04-10T09:15:02.900Z',
        endTimestamp: '2026-04-10T09:15:05.100Z',
        finishReason: 'TOOL_EXECUTION',
        messageId: 'msg_loan_02',
        reasoning:
          'Credit score is 742 (Good) and income is verified at €85,000/year. Now I need to assess the risk profile given the loan amount and check for fraud flags before making a recommendation.',
        agentMessage:
          'Credit and income look good. Checking risk profile and fraud database.',
        toolCalls: [
          {
            toolName: 'Check risk profile',
            toolElementId: 'check_risk_profile',
            toolDescription:
              'Assesses the risk profile based on applicant data and loan parameters',
            rationale:
              'Need a comprehensive risk assessment combining credit, income, and loan parameters.',
            input: {applicantId: '78432', loanAmount: 250000},
            output: {
              riskLevel: 'Moderate',
              debtToIncomeRatio: 0.31,
              loanToValueRatio: 0.8,
              recommendation: 'APPROVE_WITH_CONDITIONS',
              conditions: ['Standard mortgage insurance required'],
            },
            status: 'COMPLETED',
            duration: '0.9s',
          },
          {
            toolName: 'Check fraud database',
            toolElementId: 'check_fraud',
            toolDescription:
              'Checks the applicant against known fraud databases and watchlists',
            rationale:
              'Mandatory fraud check required before any loan approval.',
            input: {applicantId: '78432'},
            output: {
              applicantId: '78432',
              fraudFlags: [],
              watchlistMatch: false,
              identityVerified: true,
              lastChecked: '2026-04-10T09:15:04.500Z',
            },
            status: 'COMPLETED',
            duration: '1.4s',
          },
        ],
        tokenUsage: {input: 890, output: 112, reasoning: 56},
      },
      {
        iterationNumber: 3,
        startTimestamp: '2026-04-10T09:15:05.200Z',
        endTimestamp: '2026-04-10T09:15:07.500Z',
        finishReason: 'STOP',
        messageId: 'msg_loan_03',
        reasoning:
          'All checks are complete. Credit score 742 (Good), income verified at €85,000, risk level Moderate with DTI of 0.31, no fraud flags. I recommend approval with standard mortgage insurance. I should notify the lending team via Slack.',
        agentMessage:
          'Loan evaluation complete. Recommending approval with standard terms. Notifying the lending team.',
        toolCalls: [
          {
            toolName: 'Send Slack notification',
            toolElementId: 'notify_slack',
            toolDescription:
              'Sends a notification to the lending team Slack channel',
            rationale:
              'Notify the lending team about the completed assessment and recommendation.',
            input: {
              slackMessage:
                'Loan APP-2026-4821 for Maria Santos (€250,000 home purchase, 360mo): APPROVE WITH CONDITIONS. Credit: 742, Income: €85k verified, DTI: 0.31, Risk: Moderate, Fraud: Clear. Condition: Standard mortgage insurance.',
            },
            output: {
              ok: true,
              channel: '#loan-assessments',
              ts: '1712740507.000200',
            },
            status: 'COMPLETED',
            duration: '0.8s',
          },
        ],
        tokenUsage: {input: 1340, output: 186, reasoning: 72},
      },
    ],
    usage: {
      modelCalls: {current: 3, limit: 10},
      tokensUsed: {
        inputTokens: 2750,
        outputTokens: 393,
        reasoningTokens: 170,
        totalTokens: 3313,
      },
      toolsCalled: {current: 5, limit: 10},
    },
    toolDefinitions: [
      {
        name: 'Get credit score',
        description:
          'Retrieves the credit score for a given applicant from the credit bureau',
        parameters: {
          type: 'object',
          properties: {
            applicantId: {type: 'string', description: 'The applicant ID'},
          },
          required: ['applicantId'],
        },
      },
      {
        name: 'Verify income',
        description:
          "Verifies the applicant's income against employer and tax records",
        parameters: {
          type: 'object',
          properties: {
            applicantId: {type: 'string', description: 'The applicant ID'},
          },
          required: ['applicantId'],
        },
      },
      {
        name: 'Check risk profile',
        description:
          'Assesses the risk profile based on applicant data and loan parameters',
        parameters: {
          type: 'object',
          properties: {
            applicantId: {type: 'string', description: 'The applicant ID'},
            loanAmount: {
              type: 'number',
              description: 'The requested loan amount',
            },
          },
          required: ['applicantId', 'loanAmount'],
        },
      },
      {
        name: 'Check fraud database',
        description:
          'Checks the applicant against known fraud databases and watchlists',
        parameters: {
          type: 'object',
          properties: {
            applicantId: {type: 'string', description: 'The applicant ID'},
          },
          required: ['applicantId'],
        },
      },
      {
        name: 'Send Slack notification',
        description: 'Sends a notification to the lending team Slack channel',
        parameters: {
          type: 'object',
          properties: {
            slackMessage: {type: 'string', description: 'The message to send'},
          },
          required: ['slackMessage'],
        },
      },
    ],
    conversation: [
      {
        role: 'system',
        content: [
          'You are a **Risk Assessment Agent** for a financial services company. You evaluate loan applications by checking credit scores, verifying income, assessing risk profiles, and checking fraud databases. Use the provided tools to gather all necessary data before making a recommendation.',
        ],
      },
      {
        role: 'user',
        content: [
          'Evaluate loan application APP-2026-4821 for applicant Maria Santos (ID: 78432). Requested amount: €250,000. Purpose: Home purchase. Term: 360 months.',
        ],
        timestamp: '2026-04-10T09:15:00.200Z',
      },
      {
        role: 'assistant',
        content: [
          "Starting loan evaluation for Maria Santos. I'll check the credit score and verify income first, since these are independent checks.\n\n<thinking>\n<context>New loan application to evaluate. Need credit, income, risk, and fraud data.</context>\n<reflection>Get credit score and verify income can run in parallel as a first step.</reflection>\n</thinking>",
        ],
        timestamp: '2026-04-10T09:15:01.000Z',
        toolCalls: [
          {
            id: 'toolu_loan_01_credit',
            name: 'get_credit_score',
            arguments: {applicantId: '78432'},
          },
          {
            id: 'toolu_loan_01_income',
            name: 'verify_income',
            arguments: {applicantId: '78432'},
          },
        ],
      },
      {
        role: 'tool_call_result',
        content: [],
        timestamp: '2026-04-10T09:15:02.800Z',
        toolResults: [
          {
            id: 'toolu_loan_01_credit',
            name: 'get_credit_score',
            content: JSON.stringify({
              applicantId: '78432',
              score: 742,
              rating: 'Good',
              delinquencies: 0,
            }),
          },
          {
            id: 'toolu_loan_01_income',
            name: 'verify_income',
            content: JSON.stringify({
              applicantId: '78432',
              annualIncome: 85000,
              currency: 'EUR',
              employer: 'TechCorp GmbH',
              verified: true,
            }),
          },
        ],
      },
      {
        role: 'assistant',
        content: [
          'Credit and income look good. Credit score is 742 (Good) and income is verified at €85,000/year. Now checking risk profile and fraud database.\n\n<thinking>\n<context>Credit: 742 Good. Income: €85k verified. Need risk assessment and fraud check.</context>\n<reflection>Check risk profile and fraud database in parallel as the next step.</reflection>\n</thinking>',
        ],
        timestamp: '2026-04-10T09:15:03.000Z',
        toolCalls: [
          {
            id: 'toolu_loan_02_risk',
            name: 'check_risk_profile',
            arguments: {applicantId: '78432', loanAmount: 250000},
          },
          {
            id: 'toolu_loan_02_fraud',
            name: 'check_fraud',
            arguments: {applicantId: '78432'},
          },
        ],
      },
      {
        role: 'tool_call_result',
        content: [],
        timestamp: '2026-04-10T09:15:05.100Z',
        toolResults: [
          {
            id: 'toolu_loan_02_risk',
            name: 'check_risk_profile',
            content: JSON.stringify({
              riskLevel: 'Moderate',
              debtToIncomeRatio: 0.31,
              recommendation: 'APPROVE_WITH_CONDITIONS',
            }),
          },
          {
            id: 'toolu_loan_02_fraud',
            name: 'check_fraud',
            content: JSON.stringify({
              fraudFlags: [],
              watchlistMatch: false,
              identityVerified: true,
            }),
          },
        ],
      },
      {
        role: 'assistant',
        content: [
          'Loan evaluation complete. All checks passed. Recommending approval with standard mortgage insurance. Notifying the lending team.\n\n<thinking>\n<context>All data gathered. Credit 742, income verified, moderate risk, no fraud.</context>\n<reflection>Should notify the lending team via Slack with the recommendation.</reflection>\n</thinking>',
        ],
        timestamp: '2026-04-10T09:15:05.500Z',
        toolCalls: [
          {
            id: 'toolu_loan_03_slack',
            name: 'notify_slack',
            arguments: {
              slackMessage:
                'Loan APP-2026-4821 for Maria Santos: APPROVE WITH CONDITIONS. Credit: 742, Income: €85k, DTI: 0.31, Risk: Moderate, Fraud: Clear.',
            },
          },
        ],
      },
      {
        role: 'tool_call_result',
        content: [],
        timestamp: '2026-04-10T09:15:07.500Z',
        toolResults: [
          {
            id: 'toolu_loan_03_slack',
            name: 'notify_slack',
            content: JSON.stringify({ok: true, channel: '#loan-assessments'}),
          },
        ],
      },
      {
        role: 'assistant',
        content: [
          'Loan evaluation for Maria Santos (APP-2026-4821) is complete. Recommendation: **Approve with conditions** (standard mortgage insurance required). The lending team has been notified via Slack.',
        ],
        timestamp: '2026-04-10T09:15:07.500Z',
      },
    ],
  },
};
