/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ActionableNotification} from '@carbon/react';
import {storeStateLocally} from 'modules/utils/localStorage';

type Props = {
  type: 'Input' | 'Output';
  text: string;
  onClose: () => void;
};

const IOMappingInfoBanner: React.FC<Props> = ({type, text, onClose}) => {
  return (
    <ActionableNotification
      kind="info"
      inline
      lowContrast
      subtitle={text}
      actionButtonLabel="Learn more"
      onActionButtonClick={() => {
        window.open(
          'https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings',
          '_blank',
        );
      }}
      onClose={() => {
        onClose();
        storeStateLocally({[`hide${type}MappingsHelperBanner`]: true});
      }}
    />
  );
};

export {IOMappingInfoBanner};
