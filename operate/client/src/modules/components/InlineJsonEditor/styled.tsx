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
import {InlineLoading} from '@carbon/react';

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
  position: relative;

  ${({$invalid}) =>
    $invalid &&
    css`
      .cds--form-requirement {
        display: block;
        overflow: visible;
        font-weight: 400;
        max-block-size: 12.5rem;
        color: var(--cds-text-error);
      }
    `}
`;

const EditorLoader = styled.div<{$height: number}>`
  height: ${({$height}) => $height}px;
`;

const ReadOnlyEditorWrapper = styled.div<{
  $height: number;
  $empty: boolean;
  $editMode: boolean;
  $scrollable: boolean;
  $invalid: boolean;
}>`
  position: relative;
  width: 100%;
  min-height: ${EDITOR_MIN_HEIGHT}px;
  cursor: pointer;
  padding-top: ${EDITOR_PADDING_TOP}px;
  padding-bottom: ${EDITOR_PADDING_BOTTOM}px;
  letter-spacing: 0;

  ${({$scrollable, $height}) =>
    $scrollable
      ? css`
          max-height: ${$height}px;
          overflow-y: auto;

          &::-webkit-scrollbar {
            width: 12px;
          }
          &::-webkit-scrollbar-track {
            background: transparent;
            border-radius: 0;
          }
          &::-webkit-scrollbar-thumb {
            background: var(--monaco-scrollbar-thumb);
            border-radius: 0;
          }
          &::-webkit-scrollbar-thumb:hover {
            background: var(--monaco-scrollbar-thumb-hover);
          }
        `
      : css`
          height: auto;
          overflow: visible;
        `}

  ${({$invalid}) =>
    $invalid &&
    css`
      &::after {
        ${ring('var(--cds-support-error)')}
      }
    `};

  ${({$empty}) =>
    $empty &&
    css`
      color: var(--cds-text-placeholder);
    `};

  ${({$editMode}) =>
    $editMode &&
    css`
      background-color: var(--cds-field);
      padding-left: ${EDITOR_DECORATION_WIDTH}px;
      border-block-end: 1px solid var(--cds-border-strong);
    `};
`;

const ReadOnlyEditorContainer = styled.div`
  position: relative;
  width: 100%;
`;

const CopyIcon = styled.span`
  position: absolute;
  top: ${EDITOR_PADDING_TOP}px;
  right: 0;
  padding: 0.25rem;
  color: var(--cds-icon-secondary);
  opacity: 0;
  transition: opacity 0.2s ease-in-out;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;

  ${ReadOnlyEditorContainer}:hover & {
    opacity: 1;
  }
`;

const CopyLoadingIcon = styled(InlineLoading)`
  position: absolute;
  display: block;
  top: ${EDITOR_PADDING_TOP}px;
  right: 0;
  min-block-size: auto;
  inline-size: auto;
`;

const ReadOnlyEditorContent = styled.pre`
  font-size: ${EDITOR_FONT_SIZE}px;
  line-height: ${EDITOR_LINE_HEIGHT}px;
  font-family: ${EDITOR_FONT_FAMILY};
  tab-size: 2;
  text-wrap: wrap;

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: -2px;
  }
`;

const WriteModeEditor = styled.div<{
  $invalid?: boolean;
}>`
  .monaco-editor {
    width: 100% !important;
    --vscode-editor-background: var(--cds-field) !important;
    --vscode-editorGutter-background: var(--cds-field) !important;
    background-color: var(--cds-field) !important;

    &:focus-within::after {
      ${ring('var(--cds-focus)')}
    }

    ${({$invalid}) =>
      $invalid &&
      css`
        &:focus-within::after {
          ${ring('var(--cds-support-error)')}
        }
      `}
  }
`;

export {
  EditorWrapper,
  EditorLoader,
  ReadOnlyEditorContainer,
  ReadOnlyEditorWrapper,
  ReadOnlyEditorContent,
  CopyIcon,
  CopyLoadingIcon,
  WriteModeEditor,
};
