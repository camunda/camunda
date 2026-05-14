/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {TextArea, Button, InlineLoading, Tag} from '@carbon/react';
import {PromptSection, PromptRow, PromptSuggestions} from './styled';

type Props = {
  value: string;
  onChange: (value: string) => void;
  /**
   * Called when the user submits a prompt. `fromPill: true` indicates the
   * prompt came from a clicked suggestion pill — those route to the static
   * preset templates (instant, deterministic). Free-text submits go to the
   * real LLM.
   */
  onSubmit: (promptOverride?: string, options?: {fromPill?: boolean}) => void;
  isLoading: boolean;
};

/**
 * Curated example prompts shown as clickable pills below the textarea.
 * Each one demonstrates a different widget type or bundle. Clicking populates
 * the textarea and submits — they map to the keyword presets in presets.ts.
 *
 * Re-order to feature the most cinematic demos first.
 */
const SUGGESTIONS: Array<{label: string; prompt: string}> = [
  {label: 'Showcase all widgets', prompt: 'Show me everything'},
  {
    label: 'Monday morning view',
    prompt: 'Set me up with a Monday morning view',
  },
  {label: 'Process health overview', prompt: 'Process health overview'},
  {label: 'Triage', prompt: "Something looks off, what's broken?"},
  {label: 'List all incidents', prompt: 'List all incidents'},
  {label: 'Where are instances stuck?', prompt: 'Where are instances stuck?'},
  {
    label: 'Compare all processes',
    prompt: 'Compare all processes side by side',
  },
  {label: 'Workload', prompt: 'How is the workload looking?'},
  {
    label: 'Worker capacity',
    prompt: 'Show me the worker capacity for each job type',
  },
  {
    label: 'Top errors',
    prompt: 'What error types are happening most?',
  },
  {
    label: 'Slowest instances',
    prompt: 'What are the slowest-running process instances?',
  },
  {label: 'Live activity stream', prompt: 'Show me a live activity stream'},
  {label: 'Trends over time', prompt: 'Show me trends over time'},
];

const PromptInput: React.FC<Props> = ({
  value,
  onChange,
  onSubmit,
  isLoading,
}) => {
  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      onSubmit();
    }
  };

  // Click → populate the textarea and submit immediately. The `fromPill`
  // flag tells the dispatcher to use the static preset templates instead of
  // calling the real LLM (instant + deterministic). We also pass the prompt
  // explicitly to bypass the parent's stale-state closure problem.
  const handleSuggestion = (prompt: string) => {
    onChange(prompt);
    onSubmit(prompt, {fromPill: true});
  };

  return (
    <PromptSection>
      <PromptSuggestions>
        {SUGGESTIONS.map((s) => (
          <Tag
            key={s.label}
            type="cool-gray"
            size="md"
            onClick={() => handleSuggestion(s.prompt)}
            disabled={isLoading}
            style={{cursor: isLoading ? 'not-allowed' : 'pointer'}}
          >
            {s.label}
          </Tag>
        ))}
      </PromptSuggestions>
      <TextArea
        id="notebook-prompt"
        labelText="Ask anything about your processes"
        placeholder="e.g. how many incidents in payment-process?"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        rows={3}
        disabled={isLoading}
      />
      <PromptRow>
        {isLoading && <InlineLoading description="Generating widgets…" />}
        <Button
          kind="primary"
          onClick={() => onSubmit()}
          disabled={isLoading || value.trim() === ''}
        >
          Generate
        </Button>
      </PromptRow>
    </PromptSection>
  );
};

export {PromptInput};
