/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import PropTypes from 'prop-types';

import {OPERATION_TYPE} from 'modules/constants';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import {operationsStore} from 'modules/stores/operations';

import OperationItems from 'modules/components/OperationItems';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const IncidentOperation = observer(({instanceId, incident, showSpinner}) => {
  const [isSpinnerVisible, setIsSpinnerVisible] = useState(false);

  const handleOnClick = async (e) => {
    e.stopPropagation();
    setIsSpinnerVisible(true);

    // incidents operations should listen to main btn who publishes the incident ids which are affected
    operationsStore.applyOperation(instanceId, {
      operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
      incidentId: incident.id,
    });
  };

  return (
    <Styled.Operations>
      {(isSpinnerVisible || showSpinner) && (
        <OperationSpinner data-test="operation-spinner" />
      )}
      <OperationItems>
        <OperationItems.Item
          type={OPERATION_TYPE.RESOLVE_INCIDENT}
          onClick={handleOnClick}
          data-test="retry-incident"
          title="Retry Incident"
        />
      </OperationItems>
    </Styled.Operations>
  );
});

IncidentOperation.propTypes = {
  incident: PropTypes.object.isRequired,
  instanceId: PropTypes.string.isRequired,
  showSpinner: PropTypes.bool,
};

export {IncidentOperation};
