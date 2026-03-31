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
  EDITOR_MIN_HEIGHT,
  EDITOR_PADDING_BOTTOM,
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
  $invalid?: boolean;
}>`
  height: 100%;
  position: relative;

  .monaco-editor {
    width: 100% !important;
    --vscode-editor-background: var(--cds-field) !important;
    --vscode-editorGutter-background: var(--cds-field) !important;
    background-color: var(--cds-field) !important;

    &:focus-within::after {
      ${ring('var(--cds-focus, #0f62fe)')}
    }
  }

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

const EditorLoader = styled.div<{$height: number}>`
  height: ${({$height}) => $height}px;
`;

const EditorReadonly = styled.pre<{
  $height: number;
  $empty: boolean;
  $editMode: boolean;
  $scrollable: boolean;
}>`
  display: block;
  text-wrap: wrap;
  width: 100%;
  min-height: ${EDITOR_MIN_HEIGHT}px;
  font-size: ${EDITOR_FONT_SIZE}px;
  line-height: ${EDITOR_LINE_HEIGHT}px;
  font-family: ${EDITOR_FONT_FAMILY};
  padding-top: ${EDITOR_PADDING_TOP}px;
  padding-bottom: ${EDITOR_PADDING_BOTTOM}px;
  tab-size: 2;
  background-color: var(--cds-field);

  ${({$scrollable, $height}) =>
    $scrollable
      ? css`
          max-height: ${$height}px;
          overflow-y: auto;
        `
      : css`
          height: auto;
          overflow: visible;
        `}

  &:focus-visible {
    outline: 2px solid var(--cds-focus, #0f62fe);
    outline-offset: -2px;
  }

  ${({$empty}) =>
    $empty &&
    css`
      color: var(--cds-text-placeholder, #a8a8a8);
    `};
  ${({$editMode}) =>
    $editMode &&
    css`
      padding-left: ${EDITOR_DECORATION_WIDTH}px;
    `};
`;

export {EditorWrapper, EditorLoader, EditorReadonly};
