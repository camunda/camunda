/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {DocumentContent} from './DocumentContent';
import {DocumentProcessingGroup} from './styled';
import {EvidenceCards} from '../EvidenceCard';

type ContentItem = AgentInstanceHistoryItem['content'][number];

const DocumentProcessing: React.FC<{
  input: ContentItem[];
  output: ContentItem[];
  showAll?: boolean;
}> = ({input, output, showAll}) => (
  <DocumentProcessingGroup aria-label="PDF parsing and CSV export">
    <p>
      Observed steps in this user turn, not a single tool call or verified
      document lineage.
    </p>
    <section aria-label="PDF input">
      <h6>PDF input - provider parsing</h6>
      {showAll && <EvidenceCards content={input} />}
      <DocumentContent
        content={input}
        modalTitleSuffix="PDF input"
        showAll={showAll}
      />
    </section>
    <section aria-label="CSV output">
      <h6>CSV output - sandbox_export_document</h6>
      {showAll && <EvidenceCards content={output} />}
      <DocumentContent
        content={output}
        modalTitleSuffix="CSV export"
        showAll={showAll}
      />
    </section>
  </DocumentProcessingGroup>
);

export {DocumentProcessing};
