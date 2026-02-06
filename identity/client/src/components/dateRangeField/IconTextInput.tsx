/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type CarbonIconType} from '@carbon/react/icons';
import {
  TextInput as BaseTextInput,
  IconButton as BaseIconButton,
} from '@carbon/react';
import styled, {css} from 'styled-components';

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
    background-color: var(--cds-layer-01) !important;
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
  
  input:disabled,
  input[readonly] {
    background-color: var(--cds-layer-01) !important;
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

interface Props extends React.ComponentProps<typeof BaseTextInput> {
  Icon: CarbonIconType;
  invalid?: boolean;
  onIconClick: () => void;
  buttonLabel: string;
  tooltipPosition?: React.ComponentProps<typeof BaseIconButton>['align'];
}

const IconTextInput: React.FC<Props> = ({
  Icon,
  invalid,
  onIconClick,
  buttonLabel,
  tooltipPosition = 'top-right',
  ...props
}) => {
  return (
    <Container $isInvalid={invalid}>
      <TextInput invalid={invalid} {...props} />
      <IconContainer>
        <IconButton
          kind="ghost"
          size="sm"
          onClick={onIconClick}
          label={buttonLabel}
          aria-label={buttonLabel}
          align={tooltipPosition}
        >
          <Icon />
        </IconButton>
      </IconContainer>
    </Container>
  );
};

export {IconTextInput};

