/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useContext} from 'react';

import PropTypes from 'prop-types';

import {OPERATION_TYPE, DROPDOWN_PLACEMENT} from 'modules/constants';
import pluralSuffix from 'modules/utils/pluralSuffix';
import Dropdown from 'modules/components/Dropdown';
import {instanceSelection} from 'modules/stores/instanceSelection';
import CollapsablePanelContext from 'modules/contexts/CollapsablePanelContext';

import * as Styled from './styled';
import ConfirmOperationModal from '../ConfirmOperationModal';
import useOperationApply from './useOperationApply';

const ACTION_NAMES = {
  [OPERATION_TYPE.RESOLVE_INCIDENT]: 'retry',
  [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: 'cancel',
};

const CreateOperationDropdown = ({label, selectedCount}) => {
  const [modalMode, setModalMode] = useState(null);

  const [dropdownWidth, setDropdownWidth] = useState();
  const {applyBatchOperation} = useOperationApply();
  const {expandOperations} = useContext(CollapsablePanelContext);

  const closeModal = () => {
    setModalMode(null);
  };

  const handleApplyClick = () => {
    closeModal();
    applyBatchOperation(modalMode);
    expandOperations();
  };

  const handleCancelClick = () => {
    closeModal();
    instanceSelection.reset();
  };

  const getBodyText = () =>
    `About to ${ACTION_NAMES[modalMode]} ${pluralSuffix(
      selectedCount,
      'Instance'
    )}.`;

  return (
    <Styled.DropdownContainer
      dropdownWidth={dropdownWidth}
      data-test="create-operation-dropdown"
    >
      <Dropdown
        buttonStyles={Styled.dropdownButtonStyles}
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

CreateOperationDropdown.propTypes = {
  label: PropTypes.string.isRequired,
  selectedCount: PropTypes.number.isRequired,
};

export default CreateOperationDropdown;
