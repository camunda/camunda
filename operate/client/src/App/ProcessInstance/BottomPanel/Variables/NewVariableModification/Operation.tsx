/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useField} from 'react-final-form';
import {createNewVariableFieldName} from '../createVariableFieldName';

import {modificationsStore} from 'modules/stores/modifications';
import {Button} from '@carbon/react';
import {TrashCan} from '@carbon/react/icons';
import {Operations} from '../Operations';
import {useNewScopeKeyForElement} from 'modules/hooks/modifications';
import {useVariableScopeKey} from 'modules/hooks/variables';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type Props = {
  variableName: string;
  onRemove: () => void;
};

const Operation: React.FC<Props> = ({variableName, onRemove}) => {
  const {
    input: {value: currentId},
  } = useField(createNewVariableFieldName(variableName, 'id'));
  const {selectedElementId} = useProcessInstanceElementSelection();
  const newScopeKeyForElement = useNewScopeKeyForElement(selectedElementId);
  const scopeKey = useVariableScopeKey(newScopeKeyForElement);
  return (
    <Operations>
      <Button
        kind="ghost"
        size="sm"
        hasIconOnly
        renderIcon={TrashCan}
        iconDescription="Delete Variable"
        tooltipPosition="left"
        onClick={() => {
          onRemove();
          modificationsStore.removeVariableModification(
            scopeKey!,
            currentId,
            'ADD_VARIABLE',
            'variables',
          );
        }}
      />
    </Operations>
  );
};

export {Operation};
