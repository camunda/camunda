/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  TextInput as BaseTextInput,
  TextArea as BaseTextArea,
  IconButton as BaseIconButton,
} from '@carbon/react';

const IconButton = styled(BaseIconButton)`
  min-height: calc(2rem - 4px);
  margin: 2px 0;
`;

const IconContainer = styled.div<{$isTextArea?: boolean}>`
  position: absolute;

  ${({$isTextArea}) =>
    $isTextArea
      ? css`
          top: 26px;
        `
      : css`
          bottom: 0;
        `}

  right: 0;
`;

const getInputStyles = (invalid?: boolean) => {
  return invalid
    ? css`
        padding-right: var(--cds-spacing-11);
      `
    : css`
        padding-right: var(--cds-spacing-07);
      `;
};

const TextInput = styled(BaseTextInput)`
  input {
    ${({invalid}) => getInputStyles(invalid)}
  }
`;
const TextArea = styled(BaseTextArea)`
  textarea {
    ${({invalid}) => getInputStyles(invalid)}
  }
`;

const Container = styled.div<{$isInvalid?: boolean}>`
  position: relative;

  ${IconContainer} {
    ${({$isInvalid}) =>
      $isInvalid &&
      css`
        bottom: 20px;
        right: var(--cds-spacing-08);
      `}}
  }
`;

export {Container, TextInput, TextArea, IconContainer, IconButton};
