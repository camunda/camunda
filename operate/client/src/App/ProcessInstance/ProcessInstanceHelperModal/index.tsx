/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HelperModal} from 'modules/components/HelperModal';

const localStorageKey = 'hideProcessInstanceHelperModal';

type Props = {
  open: boolean;
  onClose: () => void;
};

const ProcessInstanceHelperModal: React.FC<Props> = ({open, onClose}) => {
  return (
    <HelperModal
      title="New Process Instance Details"
      localStorageKey={localStorageKey}
      onClose={onClose}
      open={open}
      onSubmit={() => {
        window.open(
          'https://camunda.com/blog/tag/camunda-platform-8/',
          '_blank',
          'noopener,noreferrer',
        );
        onClose();
      }}
      primaryButtonText="Learn more"
      secondaryButtonText="Dismiss"
    >
      <p>
        This page shows the details of a process instance, including its current
        state, variables, and incident information.
      </p>
    </HelperModal>
  );
};

export {ProcessInstanceHelperModal};
