/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {useParams} from 'react-router-dom';
import {InlineNotification} from '@carbon/react';
import {generateWidgets} from './llm';
import {loadNotebook, saveNotebook} from './persistence';
import {PromptInput} from './PromptInput';
import {WidgetRenderer} from './WidgetRenderer';
import {PageContainer, NotebookTitle, WidgetsGrid} from './styled';
import type {Notebook, WidgetConfig} from './types';

const API_KEY: string =
  (import.meta.env['VITE_ANTHROPIC_API_KEY'] as string | undefined) ?? '';

function createFreshNotebook(id: string): Notebook {
  return {
    id,
    title: 'Untitled notebook',
    widgets: [],
    updatedAt: Date.now(),
  };
}

const NotebookPage: React.FC = () => {
  const {id = 'default'} = useParams<{id: string}>();

  const [notebook, setNotebook] = useState<Notebook>(() => {
    const loaded = loadNotebook(id);
    if (loaded) {
      return loaded;
    }
    const fresh = createFreshNotebook(id);
    saveNotebook(fresh);
    return fresh;
  });

  const [prompt, setPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async () => {
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt || isLoading) {
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const newWidgets: WidgetConfig[] = await generateWidgets(
        trimmedPrompt,
        API_KEY,
      );

      setNotebook((prev) => {
        const isFirstPrompt =
          prev.widgets.length === 0 && prev.title === 'Untitled notebook';
        const updated: Notebook = {
          ...prev,
          title: isFirstPrompt ? trimmedPrompt.slice(0, 50) : prev.title,
          widgets: [...prev.widgets, ...newWidgets],
          updatedAt: Date.now(),
        };
        saveNotebook(updated);
        return updated;
      });

      setPrompt('');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to generate widgets.';
      setErrorMessage(message);
    } finally {
      setIsLoading(false);
    }
  };

  const hasContent = notebook.widgets.length > 0;
  const hasCustomTitle = notebook.title !== 'Untitled notebook';

  return (
    <PageContainer>
      {(hasContent || hasCustomTitle) && (
        <NotebookTitle>{notebook.title}</NotebookTitle>
      )}

      {errorMessage && (
        <InlineNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
        />
      )}

      <WidgetsGrid>
        {notebook.widgets.map((widget) => (
          <WidgetRenderer key={widget.id} config={widget} />
        ))}
      </WidgetsGrid>

      <PromptInput
        value={prompt}
        onChange={setPrompt}
        onSubmit={handleSubmit}
        isLoading={isLoading}
      />
    </PageContainer>
  );
};

export {NotebookPage};
