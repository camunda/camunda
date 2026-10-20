/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {AvailableTools} from './index';
import {getProviderTools} from './useAgentToolConfiguration';
import {mockAgentInstanceHistoryItem} from 'modules/mocks/mockAgentInstanceHistoryItem';
import type {AgentTool} from '@camunda/camunda-api-zod-schemas/8.10';

describe('<AvailableTools />', () => {
  it('should render a list with tool name and description for each tool', () => {
    const tools: AgentTool[] = [
      {
        name: 'get_weather',
        description: 'Returns current weather for a location.',
        elementId: null,
      },
      {
        name: 'send_email',
        description: 'Sends an email to a recipient.',
        elementId: 'Activity_2',
      },
    ];

    render(<AvailableTools tools={tools} />);

    expect(screen.getByRole('list')).toBeInTheDocument();
    const firstTool = screen.getByRole('listitem', {name: 'get_weather'});
    expect(firstTool).toBeVisible();
    expect(firstTool).toHaveTextContent(
      'Returns current weather for a location.',
    );

    const secondTool = screen.getByRole('listitem', {name: 'send_email'});
    expect(secondTool).toBeVisible();
    expect(secondTool).toHaveTextContent('Sends an email to a recipient.');
  });

  it('should render tools in alphabetical order by name', () => {
    const tools: AgentTool[] = [
      {name: 'send_email', description: null, elementId: null},
      {name: 'analyze_data', description: null, elementId: null},
      {name: 'get_weather', description: null, elementId: null},
    ];

    render(<AvailableTools tools={tools} />);

    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveAccessibleName('analyze_data');
    expect(items[1]).toHaveAccessibleName('get_weather');
    expect(items[2]).toHaveAccessibleName('send_email');
  });

  it('should render an empty hint when no tools are configured', () => {
    render(<AvailableTools tools={[]} />);

    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(
      screen.getByText('No tools available for the AI agent.'),
    ).toBeVisible();
  });

  it('should classify tools using Carbon palette category chips', () => {
    render(
      <AvailableTools
        tools={[
          {
            name: 'AskFraudRiskAgent',
            elementId: 'AskFraudRiskAgent',
            description: null,
          },
          {name: 'c8__get_document', elementId: null, description: null},
          {name: 'sandbox_bash', elementId: 'sandbox_bash', description: null},
        ]}
        providerTools={[
          {
            name: 'file-parser',
            description: 'Parses PDFs inside the provider.',
          },
        ]}
      />,
    );
    for (const [name, category, color] of [
      ['AskFraudRiskAgent', 'process', 'blue'],
      ['c8__get_document', 'camunda', 'warm-gray'],
      ['sandbox_bash', 'sandbox', 'green'],
      ['file-parser', 'provider', 'purple'],
    ]) {
      const item = within(screen.getByRole('listitem', {name}));
      expect(item.getByText(category).closest('.cds--tag')).toHaveClass(
        `cds--tag--${color}`,
      );
    }
    expect(screen.getByText('camunda').closest('.cds--tag')).toHaveClass(
      'tool-category-camunda',
    );
    expect(screen.getByText('camunda').closest('.cds--tag')).toHaveStyle({
      backgroundColor: '#ffd9be',
      color: '#8a3800',
    });
    expect(screen.getByText('Parses PDFs inside the provider.')).toBeVisible();
  });

  it('should use the dark orange palette on a dark theme', () => {
    render(
      <div className="cds--g100">
        <AvailableTools
          tools={[
            {name: 'c8__get_document', elementId: null, description: null},
          ]}
        />
      </div>,
    );
    expect(screen.getByText('camunda').closest('.cds--tag')).toHaveStyle({
      backgroundColor: '#8a3800',
      color: '#ffd9be',
    });
  });

  it('should show provider-only inventory and explain configuration is not execution', () => {
    const content = [
      {
        contentType: 'OBJECT' as const,
        object: {
          evidence: {
            kind: 'configuration',
            serverTools: {
              configured: ['web_search', 'file-parser', 'web_search'],
            },
            documentParsing: {plugin: 'file-parser', pdfEngine: 'native'},
          },
        },
      },
    ];
    const providerTools = getProviderTools([
      mockAgentInstanceHistoryItem({role: 'CONFIGURATION', content}),
    ]);
    expect(providerTools).toHaveLength(2);
    render(<AvailableTools tools={[]} providerTools={providerTools} />);
    expect(
      screen.getByRole('listitem', {name: 'file-parser'}),
    ).toHaveTextContent('PDF engine: native.');
    expect(
      screen.getByRole('listitem', {name: 'web_search'}),
    ).toHaveTextContent('Configuration alone does not confirm execution.');
    expect(
      screen.queryByText('No tools available for the AI agent.'),
    ).not.toBeInTheDocument();
  });

  it('should use latest recorded configuration and ignore malformed capabilities', () => {
    const history = [
      mockAgentInstanceHistoryItem({
        historyItemKey: '1',
        role: 'CONFIGURATION',
        content: [
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'configuration',
                serverTools: {configured: ['old_tool']},
              },
            },
          },
        ],
      }),
      mockAgentInstanceHistoryItem({
        historyItemKey: '2',
        role: 'CONFIGURATION',
        content: [
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'configuration',
                serverTools: {configured: [null, {}, 'web_search']},
                documentParsing: {plugin: null},
              },
            },
          },
        ],
      }),
    ];
    expect(getProviderTools(history).map(({name}) => name)).toEqual([
      'web_search',
    ]);
    expect(getProviderTools([])).toEqual([]);
  });
});
