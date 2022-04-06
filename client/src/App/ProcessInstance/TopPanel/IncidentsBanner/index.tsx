/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {ViewHide, BannerButton} from './styled';
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

  const errorMessage = `${pluralSuffix(incidentsCount, 'Incident')} occured`;
  const title = `View ${pluralSuffix(
    incidentsCount,
    'Incident'
  )} in Instance ${processInstanceId}`;

  return (
    <BannerButton
      data-testid="incidents-banner"
      onClick={onClick}
      title={title}
    >
      {errorMessage}
      <ViewHide>{isOpen ? 'Hide' : 'View'}</ViewHide>
    </BannerButton>
  );
});

export {IncidentsBanner};
