/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, type ReactNode} from 'react';
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
import {
  ContentsLayout,
  ContentsPane,
  ModalAttachments,
  ModalToolbar,
} from '../../styled';
import {
  ModalViewSwitcher,
  SourceEditor,
  type ModalView,
  type ModalViewProps,
} from '../../ModalView';
import {
  Description,
  Columns,
  Column,
  ColumnLabel,
  ResultContainer,
  EmptyHint,
  ToolModal,
} from './styled';

type ToolResultModalProps = ModalViewProps & {
  toolName: string;
  description: string | null;
  input: Record<string, unknown> | null;
  content: ContentItem[];
  onClose: () => void;
  navigation?: ReactNode;
  contents?: ReactNode;
};

const ToolResultModal: React.FC<ToolResultModalProps> = ({
  toolName,
  description,
  input,
  content,
  onClose,
  navigation,
  contents,
  view,
  onViewChange,
}) => {
  const [localView, setLocalView] = useState<ModalView>('preview');
  const modalView = view ?? localView;
  const setModalView = onViewChange ?? setLocalView;
  const isSource = modalView === 'source';
  const hasInput = input !== null && Object.keys(input).length > 0;
  const result = getToolCallResult(content);
  const hasEvidence = getContentEvidence(content).length > 0;
  const hasDocuments = content.some(
    (entry) => entry.contentType === 'DOCUMENT',
  );
  const outputCopyValue = isSource
    ? JSON.stringify(content, null, 2)
    : result?.copyValue;

  const body = (
    <>
      <ModalToolbar>
        <ModalViewSwitcher view={modalView} onChange={setModalView} />
      </ModalToolbar>
      {description && <Description>{description}</Description>}
      <Columns>
        <Column aria-label="Input" data-testid="tool-call-input">
          <ColumnLabel>Input</ColumnLabel>
          {(isSource || hasInput) && (
            <CopyButton
              value={JSON.stringify(input, null, 2)}
              hasIconOnly
              tooltipAlignment="end"
            />
          )}
          {isSource ? (
            <ResultContainer>
              <SourceEditor
                value={JSON.stringify(input, null, 2)}
                height="100%"
              />
            </ResultContainer>
          ) : hasInput ? (
            <ResultContainer>
              <StructuredValue value={input} />
            </ResultContainer>
          ) : (
            <EmptyHint>Tool called without input arguments.</EmptyHint>
          )}
        </Column>
        <Column aria-label="Output" data-testid="tool-call-output">
          <ColumnLabel>Output</ColumnLabel>
          {outputCopyValue !== undefined && (
            <CopyButton
              value={outputCopyValue}
              hasIconOnly
              tooltipAlignment="end"
            />
          )}
          {isSource && (
            <ResultContainer>
              <SourceEditor
                value={JSON.stringify(content, null, 2)}
                height="100%"
              />
            </ResultContainer>
          )}
          {!isSource && result === null && hasEvidence && (
            <ResultContainer>
              <EvidenceCards content={content} />
            </ResultContainer>
          )}
          {!isSource && result === null && !hasEvidence && (
            <EmptyHint>{EMPTY_RESULT_MESSAGE}</EmptyHint>
          )}
          {!isSource && result !== null && (
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
      {!isSource && hasDocuments && (
        <ModalAttachments data-testid="tool-call-documents">
          <DocumentContent
            content={content}
            modalTitleSuffix="tool result"
            showAll
          />
        </ModalAttachments>
      )}
    </>
  );

  return (
    <ToolModal
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
      className={navigation || contents ? 'agent-history-modal' : undefined}
    >
      {contents ? (
        <ContentsLayout>
          {contents}
          <ContentsPane>{body}</ContentsPane>
        </ContentsLayout>
      ) : (
        body
      )}
    </ToolModal>
  );
};

export {ToolResultModal};
