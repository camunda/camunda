/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
