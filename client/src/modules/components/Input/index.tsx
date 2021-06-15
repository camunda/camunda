/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import * as Styled from './styled';

const Input = styled((props) => {
  return <Styled.Input {...props} aria-label={props.placeholder} />;
})<React.ComponentProps<typeof Styled.Input>>``;

export {Input};
