/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';

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
    'Incident'
  )} in Instance ${processInstanceId}`;

  return (
    <StyledIncidentBanner
      data-testid="incidents-banner"
      onClick={onClick}
      title={title}
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
