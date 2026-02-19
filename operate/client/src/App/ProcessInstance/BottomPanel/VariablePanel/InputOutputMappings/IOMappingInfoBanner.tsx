/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      hasFocus={false}
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
