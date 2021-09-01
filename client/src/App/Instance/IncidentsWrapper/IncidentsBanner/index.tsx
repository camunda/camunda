/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {EXPAND_STATE} from 'modules/constants';
import {ViewHide, BannerButton} from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {incidentsStore as incidentsStoreLegacy} from 'modules/stores/incidents.legacy';
import {observer} from 'mobx-react';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';

type Props = {
  onClick: () => void;
  isOpen: boolean;
  expandState?: string;
};

const IncidentsBanner: React.FC<Props> = observer(
  ({onClick, isOpen, expandState}) => {
    const {processInstanceId} = useInstancePageParams();
    const {incidentsCount} = IS_NEXT_INCIDENTS
      ? incidentsStore
      : incidentsStoreLegacy;

    const errorMessage = `${pluralSuffix(incidentsCount, 'Incident')} occured`;
    const title = `View ${pluralSuffix(
      incidentsCount,
      'Incident'
    )} in Instance ${processInstanceId}`;

    if (expandState === EXPAND_STATE.COLLAPSED) {
      return null;
    }

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
  }
);

export {IncidentsBanner};
