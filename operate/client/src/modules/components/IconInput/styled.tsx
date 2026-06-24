/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  TextInput as BaseTextInput,
  TextArea as BaseTextArea,
  IconButton as BaseIconButton,
} from '@carbon/react';

const SCROLLBAR_WIDTH = 5;
const ICON_WIDTH = 32;
const RIGHT_SPACING = 8;

const IconButton = styled(BaseIconButton)`
  min-height: calc(2rem - 4px);
  margin: 2px 0;
`;

const IconContainer = styled.div<{$isTextArea?: boolean; $isInvalid?: boolean}>`
  position: absolute;

  ${({$isTextArea}) =>
    $isTextArea
      ? css`
          top: 26px;
        `
      : css`
          bottom: 0;
        `}

  ${({$isTextArea, $isInvalid}) => {
    if ($isTextArea) {
      return $isInvalid
        ? css`
            right: ${SCROLLBAR_WIDTH}px;
          `
        : css`
            right: calc(${RIGHT_SPACING}px + ${SCROLLBAR_WIDTH}px);
          `;
    } else {
      return css`
        right: 0;
      `;
    }
  }}
`;

const TextInput: typeof BaseTextInput = styled(BaseTextInput)`
  input {
    ${({invalid}) =>
      invalid
        ? // padding for warning icon, icon button
          css`
            padding-right: calc(
              ${ICON_WIDTH}px + ${ICON_WIDTH}px + ${RIGHT_SPACING}px
            );
          `
        : // padding for icon button
          css`
            padding-right: ${ICON_WIDTH}px;
          `}
  }
`;
const TextArea: typeof BaseTextArea = styled(BaseTextArea)`
  textarea {
    ${({invalid}) =>
      invalid
        ? // padding for warning icon, icon button and scrollbar
          css`
            padding-right: calc(
              ${ICON_WIDTH}px + ${ICON_WIDTH}px + ${SCROLLBAR_WIDTH}px
            );
          `
        : // padding for icon button and scrollbar
          css`
            padding-right: calc(
              ${ICON_WIDTH}px + ${SCROLLBAR_WIDTH}px + ${RIGHT_SPACING}px
            );
          `}
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
