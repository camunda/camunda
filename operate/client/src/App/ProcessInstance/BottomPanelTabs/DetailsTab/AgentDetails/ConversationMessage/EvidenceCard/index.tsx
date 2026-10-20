/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, isValidElement} from 'react';
import {Link, Tag} from '@carbon/react';
import {
  getContentEvidence,
  isRecord,
  type ContentItem,
  type Evidence,
} from './evidence';
import {formatInline} from '../StructuredValue/format';
import {Card, CardTitle, Fields, Label, Value, CodeValue} from './styled';

const text = (value: unknown): string | null => {
  if (value === undefined || value === null || value === '') {
    return null;
  }
  return typeof value === 'string' ? value : formatInline(value);
};

type Row = [label: string, value: unknown, code?: boolean];

const Rows: React.FC<{rows: Row[]}> = ({rows}) => (
  <Fields>
    {rows
      .filter(([, value]) => isValidElement(value) || text(value) !== null)
      .map(([label, value, code]) => (
        <Fragment key={label}>
          <Label>{label}</Label>
          {isValidElement(value) ? (
            <Value>{value}</Value>
          ) : code ? (
            <CodeValue>{text(value)}</CodeValue>
          ) : (
            <Value>{text(value)}</Value>
          )}
        </Fragment>
      ))}
  </Fields>
);

const remoteProcessInstance = (evidence: Record<string, unknown>) => {
  const key = text(evidence.remoteProcessInstanceKey);
  const url = text(evidence.remoteOperateUrl);
  if (url !== null && /^https?:\/\//.test(url)) {
    return (
      <Link href={url} target="_blank" rel="noopener noreferrer">
        {key ?? url}
      </Link>
    );
  }
  return key ?? 'not a Camunda agent / not reported';
};

const list = (value: unknown) =>
  Array.isArray(value) && value.length > 0 ? value.join(', ') : 'none';

const citation = (value: unknown) => {
  if (!isRecord(value)) {
    return text(value);
  }
  return [value.title, value.url].filter(Boolean).join(' — ');
};

const documentNames = (value: unknown) =>
  Array.isArray(value) && value.length > 0
    ? value
        .map((doc) =>
          isRecord(doc)
            ? [doc.fileName, doc.documentId ? `(${doc.documentId})` : null]
                .filter(Boolean)
                .join(' ')
            : text(doc),
        )
        .join(', ')
    : null;

const PdfParsingCard: React.FC<{parsing: Record<string, unknown>}> = ({
  parsing,
}) => (
  <Card data-testid="evidence-document-parsing">
    <CardTitle>PDF parsing</CardTitle>
    <Rows
      rows={[
        [
          'Plugin',
          parsing.plugin
            ? `${text(parsing.plugin)} (provider-run)`
            : 'provider default',
        ],
        ['Engine', parsing.pdfEngine],
        ['Sent as', parsing.sentAs],
        ['Configured via', parsing.configuredVia, true],
        ['Documents', documentNames(parsing.documents)],
      ]}
    />
  </Card>
);

type EvidenceCardProps = {
  evidence: Evidence;
};

const EvidenceCard: React.FC<EvidenceCardProps> = ({evidence}) => {
  switch (evidence.kind) {
    case 'configuration':
      return null;
    case 'serverTools': {
      const citations = Array.isArray(evidence.citations)
        ? evidence.citations
        : [];
      return (
        <Card data-testid="evidence-server-tools-used">
          <CardTitle>Provider capability used</CardTitle>
          <Rows
            rows={[
              ['Used', list(evidence.used)],
              ...citations.map<Row>((value, index) => [
                `Citation ${index + 1}`,
                citation(value),
              ]),
            ]}
          />
        </Card>
      );
    }
    case 'sandbox': {
      const fallback = evidence.fallback === true;
      return (
        <Card data-testid="evidence-sandbox">
          <CardTitle>
            Sandbox
            {fallback && (
              <Tag size="sm" type="red">
                In-process fallback, no sandbox
              </Tag>
            )}
          </CardTitle>
          <Rows
            rows={[
              ['Sandbox ID', evidence.sandboxId, true],
              ['Work dir', evidence.workDir, true],
              ['Operation', evidence.operation],
              ['Document ID', evidence.documentId, true],
              ['Path', evidence.path, true],
              ['Command', evidence.command, true],
              ['Script', evidence.script, true],
              ['Code', evidence.code, true],
              ['Exit code', evidence.exitCode],
            ]}
          />
        </Card>
      );
    }
    case 'a2a':
      return (
        <Card data-testid="evidence-a2a">
          <CardTitle>Remote A2A agent</CardTitle>
          <Rows
            rows={[
              ['Agent URL', evidence.agentUrl, true],
              ['Auth scheme', text(evidence.authScheme)],
              ['Task ID', evidence.taskId, true],
              ['Task state', evidence.state],
              ['Polls', evidence.polls],
              ['Remote process instance', remoteProcessInstance(evidence)],
            ]}
          />
        </Card>
      );
    case 'documentParsing':
      return <PdfParsingCard parsing={evidence} />;
    case 'document':
      return (
        <Card data-testid="evidence-document">
          <CardTitle>Source document</CardTitle>
          <Rows
            rows={[
              ['Name', evidence.sourceDocument],
              ['Document ID', evidence.sourceDocumentId, true],
            ]}
          />
        </Card>
      );
    default:
      return null;
  }
};

const EvidenceCards: React.FC<{
  content: ContentItem[];
}> = ({content}) => (
  <>
    {getContentEvidence(content).map((evidence, index) => (
      <EvidenceCard key={index} evidence={evidence} />
    ))}
  </>
);

export {EvidenceCards};
