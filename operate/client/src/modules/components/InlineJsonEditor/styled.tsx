/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const EditorWrapper = styled.div<{
  height: number;
  $readOnly?: boolean;
  $invalid?: boolean;
}>`
  height: ${({height}) => height}px;
  margin: var(--cds-spacing-02) 0;

  ${({$readOnly}) =>
    $readOnly &&
    `
      .monaco-editor {
        --vscode-editor-background: var(--cds-layer-01) !important;
      }
      .monaco-editor .cursors-layer > .cursor {
        display: none !important;
      }
    `}

  ${({$invalid}) =>
    $invalid &&
    `
      .monaco-editor {
        outline: 2px solid var(--cds-support-error, #da1e28) !important;
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
