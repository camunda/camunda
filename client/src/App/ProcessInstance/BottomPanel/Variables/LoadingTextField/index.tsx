/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {LoadingStateContainer} from './styled';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Loading} from '@carbon/react';

type Props = React.ComponentProps<typeof IconTextInputField> & {
  isLoading: boolean;
};

const LoadingTextfield: React.FC<Props> = ({isLoading, ...props}) => {
  if (isLoading) {
    return (
      <LoadingStateContainer>
        <Loading small data-testid="full-variable-loader" />
        <IconTextInputField {...props} />
      </LoadingStateContainer>
    );
  }

  return <IconTextInputField {...props} />;
};

export {LoadingTextfield};
