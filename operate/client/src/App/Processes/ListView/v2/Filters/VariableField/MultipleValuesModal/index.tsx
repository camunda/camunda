/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import {ERRORS} from 'modules/validators';
import {validateMultipleVariableValues} from 'modules/validators/validateMultipleVariableValues';
import {useEffect, useState} from 'react';
import {TextArea} from './styled';

type Props = {
  isVisible: boolean;
  initialValue?: string;
  onClose: () => void;
  onApply: (value: string) => void;
};

const MultipleValuesModal: React.FC<Props> = ({
  isVisible,
  initialValue,
  onClose,
  onApply,
}) => {
  const [value, setValue] = useState<string>('');
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (isVisible && initialValue !== undefined) {
      handleChange(initialValue);
    }
  }, [isVisible, initialValue]);

  const handleChange = (value: string) => {
    setValue(value);
    setError(
      validateMultipleVariableValues(value)
        ? undefined
        : ERRORS.variables.multipleValueInvalid,
    );
  };

  return (
    <Modal
      open={isVisible}
      size="lg"
      modalHeading="Edit Multiple Variable Values"
      primaryButtonText="Apply"
      secondaryButtonText="Cancel"
      onRequestClose={onClose}
      onRequestSubmit={() => onApply(value)}
      primaryButtonDisabled={error !== undefined}
    >
      <TextArea
        labelText=""
        placeholder="separated by comma"
        invalid={error !== undefined}
        invalidText={error}
        value={value}
        onChange={({target}) => handleChange(target.value)}
      />
    </Modal>
  );
};

export {MultipleValuesModal};
