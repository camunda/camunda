/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useFieldError} from 'modules/hooks/useFieldError';
import {Container, WarningIcon} from './Error.styled';

type ErrorProps = {
  name: string;
};

const Error: React.FC<ErrorProps> = ({name}) => {
  const error = useFieldError(name);

  return error === undefined ? null : (
    <Container>
      <WarningIcon title={error} />
    </Container>
  );
};

type VariableErrorProps = {
  names: [string, string];
};

const VariableError: React.FC<VariableErrorProps> = ({names}) => {
  const [firstName, secondName] = names;
  const firstFieldError = useFieldError(firstName);
  const secondFieldError = useFieldError(secondName);

  return firstFieldError === undefined &&
    secondFieldError === undefined ? null : (
    <Container>
      <WarningIcon title={firstFieldError ?? secondFieldError} />
    </Container>
  );
};

export {Error, VariableError};
