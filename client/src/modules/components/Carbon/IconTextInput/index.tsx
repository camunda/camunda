/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Icon} from '@carbon/react/icons';
import {TextInput as BaseTextInput} from '@carbon/react';
import {Container, IconContainer, TextInput, IconButton} from './styled';

interface Props extends React.ComponentProps<typeof BaseTextInput> {
  Icon: Icon;
  invalid?: boolean;
  onIconClick: () => void;
  buttonLabel: string;
}

const IconTextInput: React.FC<Props> = ({
  Icon,
  invalid,
  onIconClick,
  buttonLabel,
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
          align="top-right"
        >
          <Icon />
        </IconButton>
      </IconContainer>
    </Container>
  );
};

export {IconTextInput};
