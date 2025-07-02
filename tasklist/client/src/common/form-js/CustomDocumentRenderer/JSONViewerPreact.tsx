/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {jsx} from 'preact/jsx-runtime';
import {useEffect, useRef} from 'preact/hooks';

type Props = {
  value: string;
  height?: string;
  'data-testid'?: string;
};

function formatJSON(value: string): string {
  try {
    const parsedValue = JSON.parse(value);
    return JSON.stringify(parsedValue, null, 2);
  } catch {
    return value;
  }
}

const JSONViewerPreact = ({value, height = '300px', ...props}: Props) => {
  const preRef = useRef<HTMLPreElement>(null);
  const formattedValue = formatJSON(value);

  useEffect(() => {
    if (preRef.current) {
      preRef.current.textContent = formattedValue;
    }
  }, [formattedValue]);

  return jsx('div', {
    style: {
      border: '1px solid var(--cds-border-subtle)',
      borderRadius: '4px',
      overflow: 'hidden',
      height,
      maxHeight: height,
    },
    'data-testid': props['data-testid'],
    children: jsx('pre', {
      ref: preRef as any,
      style: {
        margin: 0,
        padding: '12px',
        fontSize: '13px',
        lineHeight: '20px',
        fontFamily: '"IBM Plex Mono", "Droid Sans Mono", "monospace", monospace, "Droid Sans Fallback"',
        overflow: 'auto',
        height: '100%',
        whiteSpace: 'pre-wrap',
        wordWrap: 'break-word',
        backgroundColor: 'var(--cds-layer)',
        color: 'var(--cds-text-primary)',
      },
      children: formattedValue,
    }),
  });
};

export {JSONViewerPreact};