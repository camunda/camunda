/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {DiffEditorModal} from 'modules/components/DiffEditorModal';
import {Container, ModalIconButton} from './styled';
import {ReactComponent as ModalIcon} from 'modules/components/Icon/modal.svg';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {TruncatedValue} from '../styled';

type Props = {
  operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
  name: string;
  oldValue: string;
  newValue: string;
};

const VariableModification: React.FC<Props> = ({
  operation,
  name,
  oldValue,
  newValue,
}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);

  return (
    <Container>
      <TruncatedValue>{`${name}: ${newValue}`}</TruncatedValue>
      <ModalIconButton
        title="Open JSON Editor Modal"
        aria-label="Open JSON Editor Modal"
        icon={<ModalIcon />}
        size="large"
        onClick={() => {
          setIsModalVisible(true);
        }}
      />

      {operation === 'ADD_VARIABLE' ? (
        <JSONEditorModal
          isVisible={isModalVisible}
          readOnly
          title={`Variable "${name}"`}
          value={newValue}
          onClose={() => {
            setIsModalVisible(false);
          }}
        />
      ) : (
        <DiffEditorModal
          isVisible={isModalVisible}
          title={`Variable "${name}"`}
          originalValue={oldValue}
          modifiedValue={newValue}
          onClose={() => {
            setIsModalVisible(false);
          }}
        />
      )}
    </Container>
  );
};

export {VariableModification};
