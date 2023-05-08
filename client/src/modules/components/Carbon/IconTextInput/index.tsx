/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Icon} from '@carbon/react/icons';
import {TextInput as BaseTextInput} from '@carbon/react';
import {Container, TextInput} from './styled';

interface Props extends React.ComponentProps<typeof BaseTextInput> {
  Icon: Icon;
}

const IconTextInput: React.FC<Props> = ({Icon, ...props}) => {
  return (
    <Container>
      <TextInput {...props} />
      <Icon />
    </Container>
  );
};

export {IconTextInput};
