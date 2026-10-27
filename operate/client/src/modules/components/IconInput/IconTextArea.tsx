/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  TextArea as BaseTextArea,
  IconButton as BaseIconButton,
} from '@carbon/react';
import type {CarbonIconType} from '@carbon/react/icons';
import {Container, IconContainer, TextArea, IconButton} from './styled';

interface Props extends React.ComponentProps<typeof BaseTextArea> {
  Icon: CarbonIconType;
  invalid?: boolean;
  onIconClick: () => void;
  buttonLabel: string;
  tooltipPosition?: React.ComponentProps<typeof BaseIconButton>['align'];
}

const IconTextArea: React.FC<Props> = ({
  Icon,
  invalid,
  onIconClick,
  buttonLabel,
  tooltipPosition = 'top-end',
  ...props
}) => {
  return (
    <Container $isInvalid={invalid}>
      <TextArea invalid={invalid} {...props} />
      <IconContainer $isTextArea $isInvalid={invalid}>
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

export {IconTextArea};
