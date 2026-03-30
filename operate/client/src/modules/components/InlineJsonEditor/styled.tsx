/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  EDITOR_DECORATION_WIDTH,
  EDITOR_FONT_FAMILY,
  EDITOR_FONT_SIZE,
  EDITOR_LINE_HEIGHT,
  EDITOR_PADDING_TOP,
} from './constants';

const ring = (color: string) => css`
  content: '';
  position: absolute;
  inset: 0;
  outline: 2px solid ${color};
  outline-offset: -2px;
  pointer-events: none;
  z-index: 1;
`;

const EditorWrapper = styled.div<{
  $readOnly?: boolean;
  $invalid?: boolean;
  $placeholder?: string;
  $empty?: boolean;
}>`
  height: 100%;
  position: relative;

  ${({$placeholder, $empty}) =>
    $placeholder &&
    $empty &&
    css`
      &:not(:focus-within)::before {
        content: '${$placeholder}';
        position: absolute;
        top: ${EDITOR_PADDING_TOP}px;
        left: ${EDITOR_DECORATION_WIDTH}px;
        color: var(--cds-text-placeholder, #a8a8a8);
        font-family: ${EDITOR_FONT_FAMILY};
        font-size: ${EDITOR_FONT_SIZE}px;
        line-height: ${EDITOR_LINE_HEIGHT}px;
        pointer-events: none;
        z-index: 1;
      }
    `}

  ${({$readOnly}) => {
    if ($readOnly) {
      return `
        .monaco-editor {
          --vscode-editor-background: var(--cds-layer-01) !important;
          --vscode-editorGutter-background: var(--cds-layer-01) !important;
          background-color: var(--cds-layer-01) !important;
        }
        .monaco-editor .cursors-layer > .cursor {
          display: none !important;
        }
      `;
    } else {
      return `
        .monaco-editor {
          --vscode-editor-background: var(--cds-field) !important;
          --vscode-editorGutter-background: var(--cds-field) !important;
          background-color: var(--cds-field) !important;
        }
      `;
    }
  }}

  ${({$readOnly}) =>
    !$readOnly &&
    css`
      &:focus-within::after {
        ${ring('var(--cds-focus, #0f62fe)')}
      }
    `}

  ${({$invalid}) =>
    $invalid &&
    css`
      &::after {
        ${ring('var(--cds-support-error, #da1e28)')}
      }
      .cds--form-requirement {
        display: block;
        overflow: visible;
        font-weight: 400;
        max-block-size: 12.5rem;
        color: var(--cds-text-error, #da1e28);
      }
    `}
`;

const EditorLoader = styled.div<{height: number}>`
  height: ${({height}) => height}px;
`;

export {EditorWrapper, EditorLoader};
