/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useForm} from 'react-final-form';
import {PendingVariable} from './PendingVariable';
import {NewVariable} from './NewVariable';
import {FooterContainer} from './styled';
import {AddVariableButton} from '../AddVariableButton';

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
    </FooterContainer>
  );
};

export {Footer};
