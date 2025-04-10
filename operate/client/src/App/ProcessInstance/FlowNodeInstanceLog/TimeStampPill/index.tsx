/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {Toggle} from './styled';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';

const TimeStampPill: React.FC = observer(() => {
  const {status: flowNodeInstanceStatus} = flowNodeInstanceStore.state;
  const {
    state: {isTimeStampVisible},
    toggleTimeStampVisibility,
  } = flowNodeTimeStampStore;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isSuccess} = useProcessInstanceXml({processDefinitionKey});

  const isDisabled = flowNodeInstanceStatus !== 'fetched' && !isSuccess;
  return (
    <Toggle
      aria-label={`${isTimeStampVisible ? 'Hide' : 'Show'} End Date`}
      id="toggle-end-date"
      labelA="Show End Date"
      labelB="Hide End Date"
      onClick={() => {
        toggleTimeStampVisibility();
        tracking.track({eventName: 'instance-history-end-time-toggled'});
      }}
      disabled={isDisabled}
      size="sm"
    />
  );
});

export {TimeStampPill};
