/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const CodeEditor = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.codeModal.codeEditor;

    return css`
      padding: 0;
      position: relative;
      counter-reset: line;
      max-height: calc(100% - 5px);

      &:before {
        content: '';
        position: fixed;
        top: 55px;
        bottom: 0;
        left: 0;
        width: 32px;
        border-right: 1px solid ${colors.borderColor};
        background-color: ${colors.backgroundColor};
      }
    `;
  }}
`;

const Pre = styled.pre`
  ${({theme}) => {
    const colors = theme.colors.modules.codeModal.codeEditor.pre;
    const opacity = theme.opacity.modules.codeModal.codeEditor;

    return css`
      width: fit-content;
      margin: 0;
      min-width: 100%;

      > code > p {
        margin: 3px;
        line-height: 14px;
        color: ${colors.color};
        font-family: IBM Plex Mono;
        font-size: 14px;

        &:before {
          left: 5px;
          position: sticky;
          overflow-x: hidden;
          font-size: 12px;
          box-sizing: border-box;
          text-align: right;
          vertical-align: top;
          line-height: 17px;
          counter-increment: line;
          content: counter(line);
          color: ${theme.colors.text02};
          display: inline-block;
          width: 35px;
          opacity: ${opacity};
          padding-right: 13px;
          -webkit-user-select: none;
        }
      }
    `;
  }}
`;

export {CodeEditor, Pre};
