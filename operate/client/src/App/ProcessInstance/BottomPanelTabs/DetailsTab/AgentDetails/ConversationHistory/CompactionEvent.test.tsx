/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {CompactionEvent} from './CompactionEvent';

describe('<CompactionEvent />', () => {
  it('should show reported savings and a method-provided message', () => {
    render(
      <CompactionEvent
        content={[
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'compaction',
                savedTokens: 12345,
                message: 'Masked tools A, B, C',
              },
            },
          },
        ]}
      />,
    );

    expect(
      screen.getByRole('article', {name: 'Context compacted'}),
    ).toHaveTextContent('Saved 12,345 tokens');
    expect(screen.getByText('Masked tools A, B, C')).toBeVisible();
  });

  it.each([undefined, null, -1, 1.5, '100', Number.MAX_SAFE_INTEGER + 1])(
    'should omit unavailable or invalid savings (%s)',
    (savedTokens) => {
      render(
        <CompactionEvent
          content={[
            {
              contentType: 'OBJECT',
              object: {evidence: {kind: 'compaction', savedTokens}},
            },
          ]}
        />,
      );

      expect(
        screen.getByRole('article', {name: 'Context compacted'}),
      ).toHaveTextContent(/^Context compacted$/);
    },
  );

  it('should show measured zero savings without requiring a method message', () => {
    render(
      <CompactionEvent
        content={[
          {
            contentType: 'OBJECT',
            object: {evidence: {kind: 'compaction', savedTokens: 0}},
          },
        ]}
      />,
    );

    expect(
      screen.getByRole('article', {name: 'Context compacted'}),
    ).toHaveTextContent('Saved 0 tokens');
  });

  it('should not render ordinary configuration or memory snapshots as compaction', () => {
    const {container} = render(
      <CompactionEvent
        content={[
          {
            contentType: 'OBJECT',
            object: {evidence: {kind: 'configuration'}},
          },
          {
            contentType: 'OBJECT',
            object: {evidence: {kind: 'customMemory', maskedToolResults: 6}},
          },
        ]}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
