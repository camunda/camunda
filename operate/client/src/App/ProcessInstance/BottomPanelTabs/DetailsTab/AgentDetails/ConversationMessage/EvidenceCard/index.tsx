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
    <CardTitle>
      PDF parsing
      <Tag size="sm" type={parsing.plugin ? 'purple' : 'gray'}>
        {parsing.plugin
          ? `${text(parsing.plugin)} plugin, provider-run`
          : 'provider default'}
      </Tag>
    </CardTitle>
    <Rows
      rows={[
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
  credentialBinding?: string | null;
};

const EvidenceCard: React.FC<EvidenceCardProps> = ({
  evidence,
  credentialBinding,
}) => {
  switch (evidence.kind) {
    case 'configuration': {
      const prompt = isRecord(evidence.systemPrompt)
        ? evidence.systemPrompt
        : {};
      const credential = isRecord(evidence.credential)
        ? evidence.credential
        : {};
      const serverTools = isRecord(evidence.serverTools)
        ? evidence.serverTools
        : {};
      return (
        <>
          <Card data-testid="evidence-system-prompt">
            <CardTitle>
              System prompt
              <Tag size="sm" type={prompt.type === 'linked' ? 'blue' : 'gray'}>
                {prompt.type === 'linked' ? 'Linked resource' : 'Inline'}
              </Tag>
            </CardTitle>
            <Rows
              rows={[
                ['Prompt ID', prompt.promptId],
                ['Version', prompt.version],
                ['Binding', prompt.binding],
                ['Resource key', prompt.resourceKey],
              ]}
            />
          </Card>
          <Card data-testid="evidence-credential">
            <CardTitle>
              Model credential
              <Tag
                size="sm"
                type={credential.mode === 'saved credential' ? 'green' : 'gray'}
              >
                {text(credential.mode) ?? 'unknown'}
              </Tag>
            </CardTitle>
            <Rows
              rows={[
                ['Credential binding', credentialBinding, true],
                ['Template', credential.template],
                ['Bound to', credential.boundTo, true],
                [
                  'Secret fields',
                  Array.isArray(credential.secretFields)
                    ? `${credential.secretFields.join(', ')} (resolved at runtime, never recorded)`
                    : null,
                ],
              ]}
            />
          </Card>
          <Card data-testid="evidence-server-tools-configured">
            <CardTitle>Provider-run server tools</CardTitle>
            <Rows rows={[['Configured', list(serverTools.configured)]]} />
          </Card>
          {isRecord(evidence.documentParsing) && (
            <PdfParsingCard parsing={evidence.documentParsing} />
          )}
        </>
      );
    }
    case 'serverTools': {
      const citations = Array.isArray(evidence.citations)
        ? evidence.citations
        : [];
      return (
        <Card data-testid="evidence-server-tools-used">
          <CardTitle>
            Server tools used
            <Tag size="sm" type="purple">
              provider-run
            </Tag>
          </CardTitle>
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
            <Tag size="sm" type={fallback ? 'red' : 'green'}>
              {fallback ? 'In-process fallback, no sandbox' : 'Daytona'}
            </Tag>
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
          <CardTitle>
            Remote A2A agent
            <Tag size="sm" type="teal">
              {text(evidence.authScheme) ?? 'no auth'}
            </Tag>
          </CardTitle>
          <Rows
            rows={[
              ['Agent URL', evidence.agentUrl, true],
              [
                'Auth scheme',
                `${text(evidence.authScheme)} (token never recorded)`,
              ],
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
  credentialBinding?: string | null;
}> = ({content, credentialBinding}) => (
  <>
    {getContentEvidence(content).map((evidence, index) => (
      <EvidenceCard
        key={index}
        evidence={evidence}
        credentialBinding={credentialBinding}
      />
    ))}
  </>
);

export {EvidenceCards};
