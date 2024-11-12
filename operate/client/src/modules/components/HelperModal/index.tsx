/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Checkbox, Modal, Stack} from '@carbon/react';
import {storeStateLocally} from 'modules/utils/localStorage';
import React from 'react';

type Props = {
  children: React.ReactNode;
  title: string;
  open: boolean;
  onClose: () => void;
  onSubmit: () => void;
  localStorageKey: string;
};

/**
 * This component renders a modal containing provided children as modal body.
 * It also adds a checkbox to the modal body, which set localStorage key to
 * true when checked.
 **/
const HelperModal: React.FC<Props> = ({
  children,
  title,
  open,
  onClose,
  onSubmit,
  localStorageKey,
}) => {
  return (
    <Modal
      open={open}
      preventCloseOnClickOutside
      modalHeading={title}
      primaryButtonText="Continue"
      secondaryButtonText="Cancel"
      onRequestSubmit={onSubmit}
      onRequestClose={onClose}
      onSecondarySubmit={onClose}
      size="md"
    >
      <Stack gap={5}>
        {children}
        <Checkbox
          labelText="Don't show this message next time"
          id="do-not-show"
          onChange={(_, {checked}) => {
            storeStateLocally({
              [localStorageKey]: checked,
            });
          }}
        />
      </Stack>
    </Modal>
  );
};

export {HelperModal};
