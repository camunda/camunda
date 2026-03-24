/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import {useVariable} from 'modules/queries/variables/useVariable';
import type {ViewFullVariableButtonProps} from '../types';
import {InlineLoading} from '../../Operations/styled';

const ViewFullVariableButtonShow: React.FC<ViewFullVariableButtonProps> = ({
  variableName,
  variableKey,
}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const {data, isLoading} = useVariable(variableKey, {
    enabled: isModalVisible,
  });
  const variableValue = data?.value;

  return isLoading ? (
    <InlineLoading data-testid="variable-operation-spinner" />
  ) : (
    <>
      <Button
        kind="ghost"
        hasIconOnly
        renderIcon={Maximize}
        size="sm"
        aria-label={`View full value of ${variableName}`}
        iconDescription={`View full value of ${variableName}`}
        tooltipPosition="left"
        onClick={async () => {
          setIsModalVisible(true);
        }}
      />
      {variableValue !== undefined && (
        <JSONEditorModal
          value={variableValue}
          isVisible={isModalVisible}
          readOnly
          onClose={() => setIsModalVisible(false)}
          title={`Full value of ${variableName}`}
        />
      )}
    </>
  );
};

export {ViewFullVariableButtonShow};
