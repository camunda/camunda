/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import type {ReactNode} from 'react';
import {CopyButton} from 'modules/components/CopyButton';
import {
  EMPTY_RESULT_MESSAGE,
  getToolCallResult,
  type ContentItem,
} from '../getRenderableResult';
import {StructuredText} from '../../StructuredValue/StructuredText';
import {StructuredValue} from '../../StructuredValue';
import {EvidenceCards} from '../../EvidenceCard';
import {getContentEvidence} from '../../EvidenceCard/evidence';
import {DocumentContent} from '../../MessageAttachments/DocumentContent';
import {ModalAttachments} from '../../styled';
import {DocumentProcessing} from '../../MessageAttachments/DocumentProcessing';
import {
  Description,
  Columns,
  Column,
  ColumnLabel,
  ResultContainer,
  EmptyHint,
} from './styled';

type ToolResultModalProps = {
  toolName: string;
  description: string | null;
  input: Record<string, unknown> | null;
  content: ContentItem[];
  onClose: () => void;
  navigation?: ReactNode;
  parsingInput?: ContentItem[];
};

const ToolResultModal: React.FC<ToolResultModalProps> = ({
  toolName,
  description,
  input,
  content,
  onClose,
  navigation,
  parsingInput,
}) => {
  const hasInput = input !== null && Object.keys(input).length > 0;
  const result = getToolCallResult(content);
  const hasEvidence = getContentEvidence(content).length > 0;

  return (
    <Modal
      open
      size="lg"
      passiveModal
      onRequestClose={onClose}
      modalHeading={
        navigation ? (
          <span className="agent-history-heading">
            <span>{`Tool call: ${toolName}`}</span>
            {navigation}
          </span>
        ) : (
          `Tool call: ${toolName}`
        )
      }
      className={navigation ? 'agent-history-modal' : undefined}
    >
      {description && <Description>{description}</Description>}
      <Columns>
        <Column aria-label="Input" data-testid="tool-call-input">
          <ColumnLabel>Input</ColumnLabel>
          {hasInput && (
            <CopyButton
              value={JSON.stringify(input, null, 2)}
              hasIconOnly
              tooltipAlignment="end"
            />
          )}
          {hasInput ? (
            <ResultContainer>
              <StructuredValue value={input} />
            </ResultContainer>
          ) : (
            <EmptyHint>Tool called without input arguments.</EmptyHint>
          )}
        </Column>
        <Column aria-label="Output" data-testid="tool-call-output">
          <ColumnLabel>Output</ColumnLabel>
          {result !== null && (
            <CopyButton
              value={result.copyValue}
              hasIconOnly
              tooltipAlignment="end"
            />
          )}
          {result === null && hasEvidence && (
            <ResultContainer>
              <EvidenceCards content={content} />
            </ResultContainer>
          )}
          {result === null && !hasEvidence && (
            <EmptyHint>{EMPTY_RESULT_MESSAGE}</EmptyHint>
          )}
          {result !== null && (
            <ResultContainer>
              {result.kind === 'structured' ? (
                <StructuredValue value={result.value} />
              ) : (
                <StructuredText content={result.value} />
              )}
            </ResultContainer>
          )}
        </Column>
      </Columns>
      <ModalAttachments>
        {parsingInput ? (
          <DocumentProcessing input={parsingInput} output={content} showAll />
        ) : (
          <DocumentContent
            content={content}
            modalTitleSuffix="tool result"
            showAll
          />
        )}
      </ModalAttachments>
    </Modal>
  );
};

export {ToolResultModal};
