/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {useParams} from 'react-router-dom';
import {InlineNotification, Button} from '@carbon/react';
import {TrashCan} from '@carbon/react/icons';
import {generateWidgets, type BedrockCredentials} from './llm';
import {loadNotebook, saveNotebook} from './persistence';
import {PromptInput} from './PromptInput';
import {WidgetRenderer} from './WidgetRenderer';
import {
  PageContainer,
  ContentScroll,
  NotebookTitle,
  NotebookHeader,
  WidgetsGrid,
  WidgetSlot,
} from './styled';
import type {Notebook, WidgetConfig} from './types';

/**
 * Map widget config to its height tier. Used by `<WidgetSlot data-tier=…>`
 * to opt TALL widgets into a shared 296px min-height so they line up in
 * the same row. Activity-feed with activityFeedSize==='hero' upgrades to
 * the HERO tier (full-width, 480px, internally scrollable).
 */
function widgetTier(widget: WidgetConfig): 'short' | 'tall' | 'hero' | 'auto' {
  if (widget.type === 'activity-feed' && widget.activityFeedSize === 'hero') {
    return 'hero';
  }
  // Radar charts span the full row; pair that with HERO height so the polygon
  // can render with a near-square aspect ratio. At TALL (296px) the radar
  // gets squashed into a thin horizontal strip.
  if (widget.type === 'chart' && widget.chartType === 'radar') {
    return 'hero';
  }
  switch (widget.type) {
    case 'metric':
    case 'trend':
      return 'short';
    case 'chart':
    case 'funnel':
    case 'activity-feed':
      return 'tall';
    case 'kpi':
      // KPI spans the full row but only needs enough height for one strip of
      // numbers. Auto tier lets the tile size to content, ~140px in practice.
      return 'auto';
    case 'bpmn':
    case 'status-grid':
      return 'hero';
    case 'table':
    case 'text':
    default:
      return 'auto';
  }
}

const BEDROCK_CREDENTIALS: BedrockCredentials = {
  arn: (import.meta.env['VITE_AWS_BEDROCK_ARN'] as string | undefined) ?? '',
  accessKeyId:
    (import.meta.env['VITE_AWS_ACCESS_KEY_ID'] as string | undefined) ?? '',
  secretAccessKey:
    (import.meta.env['VITE_AWS_SECRET_ACCESS_KEY'] as string | undefined) ?? '',
};

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
  // Pills route to static presets via {fromPill: true}; freeform → real LLM.
  const handleSubmit = async (
    promptOverride?: string,
    options: {fromPill?: boolean} = {},
  ) => {
    // Allow callers (e.g. one-click suggestion pills) to pass an explicit
    // prompt that bypasses local state — avoids stale-closure issues when the
    // textarea was just populated and submitted in the same tick.
    const trimmedPrompt = (promptOverride ?? prompt).trim();
    if (!trimmedPrompt || isLoading) {
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const newWidgets: WidgetConfig[] = await generateWidgets(
        trimmedPrompt,
        BEDROCK_CREDENTIALS,
        {fromPill: options.fromPill ?? false},
      );

      setNotebook((prev) => {
        const isFirstPrompt =
          prev.widgets.length === 0 && prev.title === 'Untitled notebook';
        const updated: Notebook = {
          ...prev,
          title: isFirstPrompt
            ? // Cap the auto-derived title at 120 chars so longer prompts
              // ("give me a deep dive dashboard for the payment process")
              // aren't clipped mid-word.
              trimmedPrompt.length > 120
              ? `${trimmedPrompt.slice(0, 117).trimEnd()}…`
              : trimmedPrompt
            : prev.title,
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

  const handleRemoveWidget = (widgetId: string) => {
    setNotebook((prev) => {
      const updated: Notebook = {
        ...prev,
        widgets: prev.widgets.filter((w) => w.id !== widgetId),
        updatedAt: Date.now(),
      };
      saveNotebook(updated);
      return updated;
    });
  };

  /**
   * Replace a widget's config in place. Called from the WidgetFrame edit
   * modal. The new config has already been Zod-validated and shares the
   * widget's original id, so React keys and the visual position are
   * preserved.
   */
  const handleUpdateWidget = (widgetId: string, next: WidgetConfig) => {
    setNotebook((prev) => {
      const updated: Notebook = {
        ...prev,
        widgets: prev.widgets.map((w) => (w.id === widgetId ? next : w)),
        updatedAt: Date.now(),
      };
      saveNotebook(updated);
      return updated;
    });
  };

  const handleClearAll = () => {
    setNotebook((prev) => {
      const updated: Notebook = {
        ...prev,
        title: 'Untitled notebook',
        widgets: [],
        updatedAt: Date.now(),
      };
      saveNotebook(updated);
      return updated;
    });
    setErrorMessage(null);
  };

  const hasContent = notebook.widgets.length > 0;
  const hasCustomTitle = notebook.title !== 'Untitled notebook';

  return (
    <PageContainer>
      <ContentScroll>
        {(hasContent || hasCustomTitle) && (
          <NotebookHeader>
            <NotebookTitle>{notebook.title}</NotebookTitle>
            {hasContent && (
              <Button
                kind="ghost"
                size="sm"
                renderIcon={TrashCan}
                onClick={handleClearAll}
              >
                Clear all widgets
              </Button>
            )}
          </NotebookHeader>
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
          {notebook.widgets.map((widget, index) => (
            <WidgetSlot
              key={widget.id}
              $type={widget.type}
              $chartType={
                widget.type === 'chart' ? widget.chartType : undefined
              }
              $activityFeedSize={
                widget.type === 'activity-feed'
                  ? (widget.activityFeedSize ?? 'tall')
                  : undefined
              }
              data-tier={widgetTier(widget)}
              data-type={widget.type}
              style={{animationDelay: `${index * 90}ms`}}
            >
              <WidgetRenderer
                config={widget}
                onRemove={() => handleRemoveWidget(widget.id)}
                onUpdate={(next) => handleUpdateWidget(widget.id, next)}
              />
            </WidgetSlot>
          ))}
        </WidgetsGrid>
      </ContentScroll>

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
