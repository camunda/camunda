/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Icon} from '@carbon/react/icons';
import {
  TextInput as BaseTextInput,
  IconButton as BaseIconButton,
} from '@carbon/react';
import {Container, IconContainer, TextInput, IconButton} from './styled';

type Props = {
  Icon: Icon;
  invalid?: boolean;
  onIconClick: () => void;
  buttonLabel: string;
  tooltipPosition?: React.ComponentProps<typeof BaseIconButton>['align'];
} & React.ComponentProps<typeof BaseTextInput>;

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
