/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';

import pluralSuffix from 'modules/utils/pluralSuffix';
import Dropdown from 'modules/components/Dropdown';
import Option from 'modules/components/Dropdown/Option';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';

import * as Styled from './styled';
import {ConfirmOperationModal} from 'modules/components/ConfirmOperationModal';
import useOperationApply from './useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';

const ACTION_NAMES: Readonly<
  Record<'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE', string>
> = {
  RESOLVE_INCIDENT: 'retry',
  CANCEL_PROCESS_INSTANCE: 'cancel',
};

type Props = {
  label: string;
  selectedCount: number;
};

const CreateOperationDropdown: React.FC<Props> = ({label, selectedCount}) => {
  const [modalMode, setModalMode] = useState<OperationEntityType | null>(null);

  const [dropdownWidth, setDropdownWidth] = useState();
  const {applyBatchOperation} = useOperationApply();

  const closeModal = () => {
    setModalMode(null);
  };

  const handleApplyClick = () => {
    closeModal();
    if (modalMode !== null) {
      applyBatchOperation(modalMode, panelStatesStore.expandOperationsPanel);
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
    } ${pluralSuffix(selectedCount, 'Instance')}.${
      modalMode === 'CANCEL_PROCESS_INSTANCE'
        ? ' In case there are called instances, these will be canceled too.'
        : ''
    } `;

  return (
    <Styled.DropdownContainer
      // @ts-expect-error ts-migrate(2769) FIXME: Property 'dropdownWidth' does not exist on type 'I... Remove this comment to see the full error message
      dropdownWidth={dropdownWidth}
      data-testid="create-operation-dropdown"
    >
      <Dropdown
        buttonStyles={Styled.dropdownButtonStyles}
        placement="top"
        label={label}
        calculateWidth={setDropdownWidth}
      >
        <Option
          onClick={() => setModalMode('RESOLVE_INCIDENT')}
          label="Retry"
        />
        <Option
          onClick={() => setModalMode('CANCEL_PROCESS_INSTANCE')}
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
