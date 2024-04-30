/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {
  IncidentBanner as StyledIncidentBanner,
  Stack,
  WarningFilled,
} from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';

type Props = {
  onClick: () => void;
  isOpen: boolean;
};

const IncidentsBanner: React.FC<Props> = observer(({onClick, isOpen}) => {
  const {processInstanceId} = useProcessInstancePageParams();
  const {incidentsCount} = incidentsStore;

  const errorMessage = `${pluralSuffix(incidentsCount, 'Incident')} occurred`;
  const title = `View ${pluralSuffix(
    incidentsCount,
    'Incident',
  )} in Instance ${processInstanceId}`;

  return (
    <StyledIncidentBanner
      data-testid="incidents-banner"
      onClick={onClick}
      title={title}
      aria-label={title}
    >
      <Stack orientation="horizontal" gap={5}>
        <WarningFilled size={24} />
        {errorMessage}
        <span className="cds--link">{isOpen ? 'Hide' : 'View'}</span>
      </Stack>
    </StyledIncidentBanner>
  );
});

export {IncidentsBanner};
