/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {http, HttpResponse} from 'msw';
import {setupServer} from 'msw/node';
import {Chatbot} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {afterAll, afterEach, beforeAll, describe, expect, it} from 'vitest';

const mockLLMConfig = {
  provider: 'openai' as const,
  apiKey: 'test-api-key',
  model: 'gpt-4o',
};

const mockMcpConfig = {
  baseUrl: '/mcp',
};

const mockOpenAIResponse = {
  choices: [
    {
      message: {
        content: 'Hello! I can help you with your processes.',
        tool_calls: null,
      },
      finish_reason: 'stop',
    },
  ],
};

const mockMcpTools = {
  tools: [
    {
      name: 'listProcessInstances',
      description: 'List process instances',
      inputSchema: {
        type: 'object',
        properties: {
          state: {type: 'string'},
        },
      },
    },
  ],
};

const server = setupServer(
  // Mock OpenAI API
  http.post('https://api.openai.com/v1/chat/completions', () => {
    return HttpResponse.json(mockOpenAIResponse);
  }),
  // Mock MCP tools discovery
  http.get('/mcp/tools', () => {
    return HttpResponse.json(mockMcpTools);
  }),
  // Mock MCP tool execution
  http.post('/mcp/tools/*', () => {
    return HttpResponse.json({result: 'Tool executed successfully'});
  })
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const renderChatbot = () => {
  return render(
    <ThemeProvider>
      <Chatbot
        llmConfig={mockLLMConfig}
        mcpConfig={mockMcpConfig}
      />
    </ThemeProvider>
  );
};

describe('Chatbot', () => {
  it('should render the toggle button', () => {
    renderChatbot();

    const toggleButton = screen.getByRole('button', {name: /open chat/i});
    expect(toggleButton).toBeInTheDocument();
  });

  it('should open the chat window when toggle is clicked', async () => {
    renderChatbot();

    const toggleButton = screen.getByRole('button', {name: /open chat/i});
    await userEvent.click(toggleButton);

    expect(screen.getByRole('dialog', {name: /chatbot/i})).toBeInTheDocument();
    expect(screen.getByText(/Camunda Assistant/i)).toBeInTheDocument();
  });

  it('should display welcome message when opened', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    expect(screen.getByText(/I'm your Camunda Assistant/i)).toBeInTheDocument();
  });

  it('should close the chat window when close button is clicked', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: /close chat/i}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should send a message and receive a response', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    const input = screen.getByPlaceholderText(/ask about your processes/i);
    await userEvent.type(input, 'Hello');

    const sendButton = screen.getByRole('button', {name: /send message/i});
    await userEvent.click(sendButton);

    // User message should appear
    expect(screen.getByText('Hello')).toBeInTheDocument();

    // Wait for assistant response
    await waitFor(() => {
      expect(screen.getByText(/I can help you with your processes/i)).toBeInTheDocument();
    });
  });

  it('should clear messages when clear button is clicked', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    // Send a message first
    const input = screen.getByPlaceholderText(/ask about your processes/i);
    await userEvent.type(input, 'Hello');
    await userEvent.click(screen.getByRole('button', {name: /send message/i}));

    await waitFor(() => {
      expect(screen.getByText('Hello')).toBeInTheDocument();
    });

    // Clear messages
    await userEvent.click(screen.getByRole('button', {name: /clear conversation/i}));

    // Messages should be cleared, welcome message should return
    expect(screen.queryByText('Hello')).not.toBeInTheDocument();
    expect(screen.getByText(/I'm your Camunda Assistant/i)).toBeInTheDocument();
  });

  it('should handle API errors gracefully', async () => {
    server.use(
      http.post('https://api.openai.com/v1/chat/completions', () => {
        return HttpResponse.json(
          {error: {message: 'Invalid API key'}},
          {status: 401}
        );
      })
    );

    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    const input = screen.getByPlaceholderText(/ask about your processes/i);
    await userEvent.type(input, 'Hello');
    await userEvent.click(screen.getByRole('button', {name: /send message/i}));

    await waitFor(() => {
      expect(screen.getByText(/OpenAI API error/i)).toBeInTheDocument();
    });
  });

  it('should submit on Enter key press', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    const input = screen.getByPlaceholderText(/ask about your processes/i);
    await userEvent.type(input, 'Hello{enter}');

    expect(screen.getByText('Hello')).toBeInTheDocument();
  });

  it('should disable send button when input is empty', async () => {
    renderChatbot();

    await userEvent.click(screen.getByRole('button', {name: /open chat/i}));

    const sendButton = screen.getByRole('button', {name: /send message/i});
    expect(sendButton).toBeDisabled();
  });
});
