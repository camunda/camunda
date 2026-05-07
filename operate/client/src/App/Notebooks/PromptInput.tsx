/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {TextArea, Button, InlineLoading} from '@carbon/react';
import {PromptSection, PromptRow} from './styled';

type Props = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  isLoading: boolean;
};

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

  return (
    <PromptSection>
      <TextArea
        id="notebook-prompt"
        labelText="Ask a question about your processes"
        placeholder="Ask about your processes… (e.g. show active process instances)"
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
          onClick={onSubmit}
          disabled={isLoading || value.trim() === ''}
        >
          Generate
        </Button>
      </PromptRow>
    </PromptSection>
  );
};

export {PromptInput};
