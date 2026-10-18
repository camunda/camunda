/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {EvidenceCards} from './index';
import {isEvidenceOnly} from './evidence';

describe('<EvidenceCards />', () => {
  it('should render which engine parsed the attached PDF', () => {
    render(
      <EvidenceCards
        content={[
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'documentParsing',
                sentAs: 'input_file content part',
                plugin: 'file-parser',
                pdfEngine: 'native',
                configuredVia: 'bodyProperties.plugins',
                documents: [
                  {
                    fileName: 'statement-2026-09.pdf',
                    documentId: 'doc-1',
                    contentType: 'application/pdf',
                  },
                ],
              },
            },
          },
        ]}
      />,
    );

    const card = within(screen.getByTestId('evidence-document-parsing'));
    expect(
      card.getByText('file-parser plugin, provider-run'),
    ).toBeInTheDocument();
    expect(card.getByText('native')).toBeInTheDocument();
    expect(card.getByText('statement-2026-09.pdf (doc-1)')).toBeInTheDocument();
  });

  it('should render linked prompt, credential and server tool configuration', () => {
    render(
      <EvidenceCards
        credentialBinding="=camunda.vars.env.cardDisputeLlm"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'configuration',
                systemPrompt: {
                  type: 'linked',
                  promptId: 'card-dispute-analyst',
                  binding: 'latest',
                  version: 3,
                  resourceKey: '2251799813690001',
                },
                credential: {
                  mode: 'saved credential',
                  template: 'io.camunda:agentic-ai-openai-custom-credential:1',
                  secretFields: ['apiKey'],
                },
                serverTools: {configured: []},
              },
            },
          },
        ]}
      />,
    );

    const prompt = within(screen.getByTestId('evidence-system-prompt'));
    expect(prompt.getByText('card-dispute-analyst')).toBeInTheDocument();
    expect(prompt.getByText('2251799813690001')).toBeInTheDocument();
    const credential = within(screen.getByTestId('evidence-credential'));
    expect(
      credential.getByText('=camunda.vars.env.cardDisputeLlm'),
    ).toBeInTheDocument();
    expect(
      credential.getByText('apiKey (resolved at runtime, never recorded)'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('evidence-server-tools-configured')).getByText(
        'none',
      ),
    ).toBeInTheDocument();
  });

  it('should flag the in-process sandbox fallback', () => {
    render(
      <EvidenceCards
        content={[
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'sandbox',
                fallback: true,
                operation: 'sandbox_bash',
                command: 'python3 deadline.py',
                exitCode: 0,
              },
            },
          },
        ]}
      />,
    );

    const sandbox = within(screen.getByTestId('evidence-sandbox'));
    expect(
      sandbox.getByText('In-process fallback, no sandbox'),
    ).toBeInTheDocument();
    expect(sandbox.getByText('python3 deadline.py')).toBeInTheDocument();
    expect(sandbox.getByText('0')).toBeInTheDocument();
  });

  it('should lift the A2A task id and state from the tool result', () => {
    render(
      <EvidenceCards
        content={[
          {
            contentType: 'OBJECT',
            object: {
              taskId: 'task-1',
              state: 'TASK_STATE_COMPLETED',
              answer: 'RISK: LOW',
              evidence: {
                kind: 'a2a',
                agentUrl: 'http://localhost:38090/a2a',
                authScheme: 'bearer',
                polls: 2,
              },
            },
          },
        ]}
      />,
    );

    const a2a = within(screen.getByTestId('evidence-a2a'));
    expect(a2a.getByText('task-1')).toBeInTheDocument();
    expect(a2a.getByText('TASK_STATE_COMPLETED')).toBeInTheDocument();
    expect(a2a.getByText('bearer (token never recorded)')).toBeInTheDocument();
  });

  it('should only treat evidence wrappers as evidence-only content', () => {
    expect(
      isEvidenceOnly({contentType: 'OBJECT', object: {evidence: {kind: 'x'}}}),
    ).toBe(true);
    expect(
      isEvidenceOnly({
        contentType: 'OBJECT',
        object: {answer: 'yes', evidence: {kind: 'a2a'}},
      }),
    ).toBe(false);
  });
});
