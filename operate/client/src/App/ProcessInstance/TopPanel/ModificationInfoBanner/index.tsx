/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Container, Text} from './styled';
import {Button} from '@carbon/react';

type Props = {
  text: string;
  button?: {
    onClick: () => void;
    label: string;
  };
};

const ModificationInfoBanner: React.FC<Props> = ({text, button}) => {
  return (
    <Container>
      <Text>{text}</Text>
      {button && (
        <Button kind="ghost" size="sm" onClick={button.onClick}>
          {button.label}
        </Button>
      )}
    </Container>
  );
};

export {ModificationInfoBanner};
