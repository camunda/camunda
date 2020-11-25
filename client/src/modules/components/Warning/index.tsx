/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {WarningIcon, Container} from './styled';

type Props = {
  title?: string;
  className?: string;
};

const Warning: React.FC<Props> = ({title, className}) => (
  <Container title={title} className={className}>
    <WarningIcon>!</WarningIcon>
  </Container>
);

export {Warning};
