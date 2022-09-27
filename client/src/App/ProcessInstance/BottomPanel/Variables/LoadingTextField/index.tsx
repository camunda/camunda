/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TextField} from 'modules/components/TextField';

import {LoadingStateContainer} from './styled';
import {VariableBackdrop} from '../VariableBackdrop';

type Props = React.ComponentProps<typeof TextField> & {
  isLoading: boolean;
};

const LoadingTextfield: React.FC<Props> = ({isLoading, ...props}) => {
  if (isLoading) {
    return (
      <LoadingStateContainer>
        <VariableBackdrop />
        <TextField {...props} />
      </LoadingStateContainer>
    );
  }

  return <TextField {...props} />;
};

export {LoadingTextfield};
