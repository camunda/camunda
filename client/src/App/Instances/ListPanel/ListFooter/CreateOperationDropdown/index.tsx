/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useContext} from 'react';

import {OPERATION_TYPE, DROPDOWN_PLACEMENT} from 'modules/constants';
import pluralSuffix from 'modules/utils/pluralSuffix';
import Dropdown from 'modules/components/Dropdown';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import CollapsablePanelContext from 'modules/contexts/CollapsablePanelContext';

import * as Styled from './styled';
import ConfirmOperationModal from '../ConfirmOperationModal';
import useOperationApply from './useOperationApply';

const ACTION_NAMES = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: 'retry',
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: 'cancel',
};

type Props = {
  label: string;
  selectedCount: number;
};

const CreateOperationDropdown = ({label, selectedCount}: Props) => {
  const [modalMode, setModalMode] = useState<OperationEntityType | null>(null);

  const [dropdownWidth, setDropdownWidth] = useState();
  const {applyBatchOperation} = useOperationApply();
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandOperations' does not exist on type... Remove this comment to see the full error message
  const {expandOperations} = useContext(CollapsablePanelContext);

  const closeModal = () => {
    setModalMode(null);
  };

  const handleApplyClick = () => {
    closeModal();
    if (modalMode !== null) {
      applyBatchOperation(modalMode, expandOperations);
    }
  };

  const handleCancelClick = () => {
    closeModal();
    instanceSelectionStore.reset();
  };

  const getBodyText = () =>
    `About to ${
      // @ts-expect-error ts-migrate(2538) FIXME: Type 'null' cannot be used as an index type.
      ACTION_NAMES[modalMode]
    } ${pluralSuffix(selectedCount, 'Instance')}.`;

  return (
    <Styled.DropdownContainer
      // @ts-expect-error ts-migrate(2769) FIXME: Property 'dropdownWidth' does not exist on type 'I... Remove this comment to see the full error message
      dropdownWidth={dropdownWidth}
      data-testid="create-operation-dropdown"
    >
      <Dropdown
        buttonStyles={Styled.dropdownButtonStyles}
        // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type '"top" | "... Remove this comment to see the full error message
        placement={DROPDOWN_PLACEMENT.TOP}
        label={label}
        calculateWidth={setDropdownWidth}
      >
        <Dropdown.Option
          onClick={() => setModalMode(OPERATION_TYPE.RESOLVE_INCIDENT)}
          label="Retry"
        />
        <Dropdown.Option
          onClick={() => setModalMode(OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE)}
          label="Cancel"
        />
      </Dropdown>
      <ConfirmOperationModal
        isVisible={!!modalMode}
        onModalClose={closeModal}
        bodyText={getBodyText()}
        onApplyClick={handleApplyClick}
        onCancelClick={handleCancelClick}
      />
    </Styled.DropdownContainer>
  );
};

export default CreateOperationDropdown;
