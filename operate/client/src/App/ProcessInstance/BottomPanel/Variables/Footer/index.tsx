/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm} from 'react-final-form';
import {PendingVariable} from './PendingVariable';
import {NewVariable} from './NewVariable';
import {FooterContainer} from './styled';
import {AddVariableButton} from './AddVariableButton';
import {CopyVariablesButton as CopyVariablesButtonV2} from './CopyVariablesButton';

type Props = {
  variant: 'initial' | 'disabled' | 'add-variable' | 'pending-variable';
};

const Footer: React.FC<Props> = ({variant}) => {
  const form = useForm();

  return (
    <FooterContainer>
      {variant === 'pending-variable' && <PendingVariable />}
      {variant === 'add-variable' && <NewVariable />}
      {['initial', 'disabled'].includes(variant) && (
        <AddVariableButton
          onClick={() => {
            form.reset({name: '', value: ''});
          }}
          disabled={variant === 'disabled'}
        />
      )}
      <CopyVariablesButtonV2 />
    </FooterContainer>
  );
};

export {Footer};
