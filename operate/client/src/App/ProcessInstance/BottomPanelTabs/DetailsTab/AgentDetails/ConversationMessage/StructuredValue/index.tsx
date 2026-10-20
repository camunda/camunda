/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment} from 'react';
import {
  formatScalar,
  getDocumentName,
  humanizeKey,
  isRecord,
  isScalar,
  omitEvidence,
} from './format';
import {Container, CodeText, Fields, Item, Items, Label, Value} from './styled';

const CODE_KEYS = new Set(['command', 'script', 'code', 'query', 'stdout']);

type NodeProps = {
  value: unknown;
  fieldName?: string;
};

const Node: React.FC<NodeProps> = ({value, fieldName}) => {
  if (isScalar(value)) {
    const text = formatScalar(value);
    if (fieldName !== undefined && CODE_KEYS.has(fieldName)) {
      return <CodeText>{text}</CodeText>;
    }
    return <>{text}</>;
  }

  const documentName = getDocumentName(value);
  if (documentName !== null) {
    return <>{documentName}</>;
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return <>None</>;
    }
    if (value.every(isScalar)) {
      return <>{value.map(formatScalar).join(', ')}</>;
    }
    return (
      <Items>
        {value.map((item, index) => (
          <Item key={index}>
            <Node value={item} />
          </Item>
        ))}
      </Items>
    );
  }

  const entries = Object.entries(value as Record<string, unknown>);
  if (entries.length === 0) {
    return <>None</>;
  }

  return (
    <Fields>
      {entries.map(([key, entry]) => {
        const isNested = !isScalar(entry) && getDocumentName(entry) === null;
        return (
          <Fragment key={key}>
            <Label $fullWidth={isNested}>{humanizeKey(key)}</Label>
            <Value $fullWidth={isNested}>
              <Node value={entry} fieldName={key} />
            </Value>
          </Fragment>
        );
      })}
    </Fields>
  );
};

type StructuredValueProps = {
  value: unknown;
  'aria-label'?: string;
};

/**
 * Renders a structured value (tool input, tool result, JSON answer) as
 * labeled fields. Story evidence is omitted because it renders as cards.
 */
const StructuredValue: React.FC<StructuredValueProps> = ({
  value,
  'aria-label': ariaLabel,
}) => {
  const cleaned = omitEvidence(value);
  return (
    <Container aria-label={ariaLabel} data-testid="structured-value">
      {isRecord(cleaned) || Array.isArray(cleaned) ? (
        <Node value={cleaned} />
      ) : (
        <Value as="p">{formatScalar(cleaned)}</Value>
      )}
    </Container>
  );
};

export {StructuredValue};
