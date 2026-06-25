/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {AvailableTools} from './index';
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

  it('should render an empty hint when no tools are configured', () => {
    render(<AvailableTools tools={[]} />);

    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(
      screen.getByText('No tools available for the AI agent.'),
    ).toBeVisible();
  });
});
