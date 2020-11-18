/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import BasicTextareaAutosize from 'react-textarea-autosize';

const TextareaStyles = ({theme}: any) => {
  const colors = theme.colors.modules.textarea;

  return css`
    display: block;
    width: 100%;
    height: 52px;
    padding: 6px 13px 4px 8px;
    border: solid 1px ${theme.colors.ui05};
    border-radius: 3px;
    background-color: ${colors.backgroundColor};
    color: ${colors.color};
    font-family: IBM Plex Sans;
    font-size: 13px;

    &::placeholder {
      color: ${colors.placeholder.color};
      font-style: italic;
    }
  `;
};

const Textarea = styled.textarea`
  ${TextareaStyles}
`;

const TextareaAutosize = styled(BasicTextareaAutosize)`
  ${TextareaStyles}
`;

export {Textarea, TextareaAutosize};
