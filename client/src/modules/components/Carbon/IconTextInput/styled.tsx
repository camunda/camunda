/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  TextInput as BaseTextInput,
  IconButton as BaseIconButton,
} from '@carbon/react';

const IconButton = styled(BaseIconButton)`
  min-height: calc(2rem - 4px);
  margin: 2px 0;
`;

const IconContainer = styled.div`
  position: absolute;
  bottom: 0;
  right: 0;
`;

const TextInput = styled(BaseTextInput)`
  input {
    padding-right: var(--cds-spacing-07);

    ${({invalid}) =>
      invalid &&
      css`
        padding-right: var(--cds-spacing-11);
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

export {Container, TextInput, IconContainer, IconButton};
