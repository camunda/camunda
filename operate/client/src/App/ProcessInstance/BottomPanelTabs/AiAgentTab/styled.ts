/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css, keyframes} from 'styled-components';

const pulse = keyframes`
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
`;

export const Container = styled.div`
  display: flex;
  height: 100%;
  overflow: hidden;
`;

export const TimelinePane = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: var(--cds-spacing-05);
  border-right: 1px solid var(--cds-border-subtle);
  min-width: 0;
`;

export const DetailPane = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: var(--cds-spacing-05);
  min-width: 0;

  .cds--accordion__content {
    padding-right: 0.2rem;
  }

  .cds--accordion__item:first-child {
    border-top: none;
  }

  .cds--accordion__title {
    font-size: var(--cds-heading-compact-01-font-size);
    font-weight: var(--cds-heading-compact-01-font-weight);
    line-height: var(--cds-heading-compact-01-line-height);
    letter-spacing: var(--cds-heading-compact-01-letter-spacing);
    width: 100%;
  }

  .cds--progress-bar__track {
    background-color: var(--cds-border-subtle-01);
  }

  .reset-usage-btn.cds--btn--icon-only {
    padding: 0;
    min-height: auto;
    block-size: auto;
    inline-size: auto;
    width: 1rem;
    height: 1rem;
  }
`;

export const SectionLabel = styled.div`
  font-size: var(--cds-label-01-font-size);
  font-weight: 600;
  letter-spacing: 0.16px;
  text-transform: uppercase;
  color: var(--cds-text-secondary);
  margin-bottom: var(--cds-spacing-03);
`;

export const IterationRow = styled.button<{$selected: boolean}>`
  all: unset;
  display: flex;
  align-items: flex-start;
  gap: var(--cds-spacing-03);
  width: 100%;
  padding: var(--cds-spacing-03) var(--cds-spacing-03);
  border-radius: 4px;
  cursor: pointer;
  transition: background 150ms;

  ${({$selected}) =>
    $selected
      ? css`
          background: var(--cds-layer-selected);
          outline: 1px solid var(--cds-border-interactive);
        `
      : css`
          &:hover {
            background: var(--cds-layer-hover);
          }
        `}
`;

export const ToolRow = styled.button<{$selected: boolean}>`
  all: unset;
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  width: 100%;
  padding: var(--cds-spacing-02) var(--cds-spacing-03);
  border-radius: 4px;
  cursor: pointer;
  font-size: var(--cds-body-compact-01-font-size);
  transition: background 150ms;

  ${({$selected}) =>
    $selected
      ? css`
          background: var(--cds-layer-selected);
          outline: 1px solid var(--cds-border-interactive);
        `
      : css`
          &:hover {
            background: var(--cds-layer-hover);
          }
        `}
`;

export const ToolCallList = styled.div`
  margin-left: var(--cds-spacing-07);
  padding-left: var(--cds-spacing-03);
  border-left: 1px solid var(--cds-border-subtle);
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--cds-spacing-02);
  margin-bottom: var(--cds-spacing-02);
`;

export const StatusIndicator = styled.div<{
  $status: 'active' | 'completed' | 'failed';
}>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 5px;

  ${({$status}) => {
    switch ($status) {
      case 'active':
        return css`
          background: var(--cds-support-success);
          animation: ${pulse} 2s ease-in-out infinite;
        `;
      case 'failed':
        return css`
          background: var(--cds-support-error);
        `;
      case 'completed':
      default:
        return css`
          background: var(--cds-icon-secondary);
        `;
    }
  }}
`;

export const TimelineLine = styled.div`
  width: 1px;
  flex: 1;
  background: var(--cds-border-subtle);
  margin: var(--cds-spacing-02) 0;
`;

export const UserMessageRow = styled.div`
  display: flex;
  align-items: flex-start;
  gap: var(--cds-spacing-03);
  margin-bottom: var(--cds-spacing-03);
`;

export const UserAvatar = styled.div`
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--cds-layer-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;

  svg {
    width: 12px;
    height: 12px;
    fill: var(--cds-icon-primary);
  }
`;

export const Mono = styled.span`
  font-family: var(--cds-code-01-font-family, 'IBM Plex Mono', monospace);
  font-size: var(--cds-code-01-font-size, 12px);
`;

export const MetaLabel = styled.div`
  font-size: var(--cds-label-01-font-size);
  font-weight: 600;
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  text-transform: uppercase;
  color: var(--cds-text-secondary);
  margin-bottom: var(--cds-spacing-02);
`;

export const CodeBlock = styled.pre`
  font-family: var(--cds-code-01-font-family, 'IBM Plex Mono', monospace);
  font-size: var(--cds-code-01-font-size, 12px);
  background: var(--cds-layer-02);
  border-radius: 4px;
  padding: var(--cds-spacing-03) var(--cds-spacing-04);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
`;

export const UsageBar = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

export const UsageBarTrack = styled.div`
  height: 6px;
  background: var(--cds-layer-02);
  border-radius: 3px;
  overflow: hidden;
`;

export const UsageBarFill = styled.div<{$percent: number}>`
  height: 100%;
  border-radius: 3px;
  transition: width 300ms;
  width: ${({$percent}) => $percent}%;
  background: var(--cds-interactive);
`;

export const DetailSection = styled.div`
  margin-bottom: var(--cds-spacing-05);
`;

export const PropertyRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: var(--cds-spacing-03);
  font-size: var(--cds-body-compact-01-font-size);
  margin-bottom: var(--cds-spacing-02);
`;

export const PropertyLabel = styled.span`
  color: var(--cds-text-secondary);
  flex-shrink: 0;
`;

export const PropertyValue = styled.span`
  color: var(--cds-text-primary);
  word-break: break-all;
`;
