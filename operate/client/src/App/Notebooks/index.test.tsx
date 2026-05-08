/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {NotebookPage} from './index';
import {saveNotebook, loadNotebook} from './persistence';
import type {Notebook, WidgetConfig} from './types';

// ---------------------------------------------------------------------------
// Mock the LLM module — we don't want real network calls in integration tests.
// ---------------------------------------------------------------------------
vi.mock('./llm', () => ({
  generateWidgets: vi.fn(),
}));

// ---------------------------------------------------------------------------
// Mock WidgetRenderer so rendering widgets is trivial and deterministic.
// ---------------------------------------------------------------------------
vi.mock('./WidgetRenderer', () => ({
  WidgetRenderer: ({config}: {config: WidgetConfig}) => (
    <div data-testid={`widget-${config.id}`}>{config.title}</div>
  ),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type WrapperProps = {children?: React.ReactNode};

function createWrapper(notebookId: string = 'test-id-123') {
  const Wrapper = ({children}: WrapperProps) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[`/notebooks/${notebookId}`]}>
        <Routes>
          <Route path="/notebooks/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
}

const GENERATED_WIDGET: WidgetConfig = {
  id: 'generated-widget-id',
  type: 'metric',
  title: 'Generated Metric',
  query: {endpoint: '/v2/process-instances/search', method: 'POST'},
  field: 'page.totalItems',
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('<NotebookPage />', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('should render the prompt input on an empty notebook', () => {
    // given – no notebook in localStorage for this id

    // when
    render(<NotebookPage />, {wrapper: createWrapper('brand-new-id')});

    // then – a text area or input for the prompt must be present
    expect(
      screen.getByRole('textbox') ?? screen.getByPlaceholderText(/prompt|ask/i),
    ).toBeInTheDocument();
  });

  it('should auto-create a new notebook in localStorage when the id is not found', () => {
    // given – localStorage is empty, id is unknown
    const notebookId = 'auto-create-id';

    // when
    render(<NotebookPage />, {wrapper: createWrapper(notebookId)});

    // then – persistence layer must now contain this notebook
    const saved = loadNotebook(notebookId);
    expect(saved).not.toBeNull();
    expect(saved?.id).toBe(notebookId);
    expect(saved?.title).toBe('Untitled notebook');
    expect(saved?.widgets).toEqual([]);
  });

  it('should load an existing notebook from localStorage and render its widgets', async () => {
    // given
    const existingNotebook: Notebook = {
      id: 'existing-nb',
      title: 'My Saved Notebook',
      widgets: [
        {
          id: 'existing-w-1',
          type: 'metric',
          title: 'Existing Widget',
          query: {endpoint: '/v2/process-instances/search', method: 'POST'},
        },
      ],
      updatedAt: Date.now(),
    };
    saveNotebook(existingNotebook);

    // when
    render(<NotebookPage />, {wrapper: createWrapper('existing-nb')});

    // then – the pre-existing widget is rendered
    expect(
      await screen.findByTestId('widget-existing-w-1'),
    ).toBeInTheDocument();
    expect(screen.getByText('Existing Widget')).toBeInTheDocument();
  });

  it('should call generateWidgets and append returned widgets to the notebook on prompt submit', async () => {
    // given
    const {generateWidgets} = await import('./llm');
    vi.mocked(generateWidgets).mockResolvedValue([GENERATED_WIDGET]);

    const {user} = render(<NotebookPage />, {
      wrapper: createWrapper('submit-test-id'),
    });

    // when – type a prompt and submit
    const promptInput =
      screen.getByRole('textbox') ?? screen.getByPlaceholderText(/prompt|ask/i);
    await user.type(promptInput, 'show running instances');

    const submitButton = screen.getByRole('button', {
      name: /submit|send|run|generate/i,
    });
    await user.click(submitButton);

    // then – widget returned by LLM appears on screen
    expect(
      await screen.findByTestId(`widget-${GENERATED_WIDGET.id}`),
    ).toBeInTheDocument();
    expect(generateWidgets).toHaveBeenCalledWith(
      expect.stringContaining('show running instances'),
      expect.anything(),
      expect.objectContaining({fromPill: expect.any(Boolean)}),
    );
  });

  it('should persist the notebook to localStorage on widget add', async () => {
    // given
    const {generateWidgets} = await import('./llm');
    vi.mocked(generateWidgets).mockResolvedValue([GENERATED_WIDGET]);
    const notebookId = 'persist-test-id';

    const {user} = render(<NotebookPage />, {
      wrapper: createWrapper(notebookId),
    });

    // when
    const promptInput =
      screen.getByRole('textbox') ?? screen.getByPlaceholderText(/prompt|ask/i);
    await user.type(promptInput, 'anything');

    const submitButton = screen.getByRole('button', {
      name: /submit|send|run|generate/i,
    });
    await user.click(submitButton);

    // wait for widget to appear before asserting localStorage
    await screen.findByTestId(`widget-${GENERATED_WIDGET.id}`);

    // then – localStorage must contain the generated widget
    await waitFor(() => {
      const saved = loadNotebook(notebookId);
      expect(saved).not.toBeNull();
      expect(saved?.widgets).toHaveLength(1);
      expect(saved?.widgets[0]?.id).toBe(GENERATED_WIDGET.id);
    });
  });
});
