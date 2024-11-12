/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container} from './styled';
import {TruncatedValue} from '../styled';

type Props = {
  name: string;
  newValue: string;
};

const VariableModification: React.FC<Props> = ({name, newValue}) => {
  return (
    <Container>
      <TruncatedValue>{`${name}: ${newValue}`}</TruncatedValue>
    </Container>
  );
};

export {VariableModification};
