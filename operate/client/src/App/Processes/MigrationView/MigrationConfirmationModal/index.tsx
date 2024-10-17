/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {Modal, TextInput} from '@carbon/react';
import {StateProps} from 'modules/components/ModalStateManager';
import {MigrationDetails} from '../MigrationDetails';

type Props = {onSubmit: () => void} & StateProps;

const MigrationConfirmationModal: React.FC<Props> = ({
  open,
  setOpen,
  onSubmit,
}) => {
  const [inputValue, setInputValue] = useState('');

  const isDisabled = inputValue !== 'MIGRATE';
  const isInvalid = !['MIGRATE', ''].includes(inputValue);

  return (
    <Modal
      primaryButtonDisabled={isDisabled}
      modalHeading="Migration confirmation"
      size="sm"
      primaryButtonText="Confirm"
      secondaryButtonText="Cancel"
      open={open}
      onRequestClose={() => setOpen(false)}
      preventCloseOnClickOutside
      onRequestSubmit={onSubmit}
      shouldSubmitOnEnter={!isDisabled}
    >
      <MigrationDetails />

      <TextInput
        autoFocus
        id="modification-confirmation"
        labelText="Type MIGRATE to confirm"
        onChange={({target}) => {
          setInputValue(target.value);
        }}
        value={inputValue}
        invalid={isInvalid}
        invalidText="Value must match MIGRATE"
      />
    </Modal>
  );
};

export {MigrationConfirmationModal};
