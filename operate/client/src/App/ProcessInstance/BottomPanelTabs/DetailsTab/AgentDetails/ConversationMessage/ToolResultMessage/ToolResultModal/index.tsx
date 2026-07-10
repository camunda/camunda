/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {Modal} from '@carbon/react';
import {CopyButton} from 'modules/components/CopyButton';
import {
  EMPTY_RESULT_MESSAGE,
  getToolCallResult,
  type ContentItem,
} from '../getRenderableResult';
import {
  Description,
  Columns,
  Column,
  ColumnLabel,
  EditorContainer,
  EmptyHint,
} from './styled';

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);

  loadMonaco();

  return {default: RichTextEditor};
});

type ToolResultModalProps = {
  toolName: string;
  description: string | null;
  input: Record<string, unknown> | null;
  content: ContentItem[];
  onClose: () => void;
};

const ToolResultModal: React.FC<ToolResultModalProps> = ({
  toolName,
  description,
  input,
  content,
  onClose,
}) => {
  const inputValue = input !== null ? JSON.stringify(input, null, 2) : null;
  const result = getToolCallResult(content);

  return (
    <Modal
      open
      modalHeading={`Tool call: ${toolName}`}
      onRequestClose={onClose}
      size="lg"
      passiveModal
    >
      {description && <Description>{description}</Description>}
      <Columns>
        <Column aria-label="Input" data-testid="tool-call-input">
          <ColumnLabel>Input</ColumnLabel>
          {inputValue !== null && (
            <CopyButton value={inputValue} hasIconOnly tooltipAlignment="end" />
          )}
          {inputValue !== null ? (
            <EditorContainer>
              <Suspense>
                <RichTextEditor
                  value={inputValue}
                  readOnly
                  language="json"
                  height="100%"
                />
              </Suspense>
            </EditorContainer>
          ) : (
            <EmptyHint>Tool called without input arguments.</EmptyHint>
          )}
        </Column>
        <Column aria-label="Output" data-testid="tool-call-output">
          <ColumnLabel>Output</ColumnLabel>
          {result !== null && (
            <CopyButton
              value={result.value}
              hasIconOnly
              tooltipAlignment="end"
            />
          )}
          {result !== null ? (
            <EditorContainer>
              <Suspense>
                <RichTextEditor
                  value={result.value}
                  readOnly
                  language={result.language}
                  height="100%"
                />
              </Suspense>
            </EditorContainer>
          ) : (
            <EmptyHint>{EMPTY_RESULT_MESSAGE}</EmptyHint>
          )}
        </Column>
      </Columns>
    </Modal>
  );
};

export {ToolResultModal};
