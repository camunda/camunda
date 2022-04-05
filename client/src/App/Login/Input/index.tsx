/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Container, Input as StyledInput} from './styled';

const Input: React.FC<
  Omit<React.InputHTMLAttributes<HTMLInputElement>, 'placeholder'> & {
    label: string;
  }
> = (props) => (
  <Container>
    <StyledInput {...props} placeholder=" " />
    <label htmlFor={props.name}>{props.label}</label>
  </Container>
);

export {Input};
