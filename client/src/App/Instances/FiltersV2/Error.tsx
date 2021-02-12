/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useField} from 'react-final-form';

import {isFieldValid} from './isFieldValid';
import {Container} from './Error.styled';
import {Warning} from 'modules/components/Warning';

type ErrorProps = {
  name: string;
};

const Error: React.FC<ErrorProps> = ({name}) => {
  const {meta} = useField(name);

  return isFieldValid(meta) ? null : (
    <Container>
      <Warning title={meta.submitError ?? meta.error} />
    </Container>
  );
};

type VariableErrorProps = {
  names: [string, string];
};

const VariableError: React.FC<VariableErrorProps> = ({names}) => {
  const [firstName, secondName] = names;
  const firstField = useField(firstName);
  const secondField = useField(secondName);

  return isFieldValid(firstField.meta) &&
    isFieldValid(secondField.meta) ? null : (
    <Container>
      <Warning
        title={
          firstField.meta.submitError ??
          secondField.meta.submitError ??
          firstField.meta.error ??
          secondField.meta.error
        }
      />
    </Container>
  );
};

export {Error, VariableError};
