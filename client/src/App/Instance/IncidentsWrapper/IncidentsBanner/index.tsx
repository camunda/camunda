/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {EXPAND_STATE} from 'modules/constants';
import * as Styled from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';

type Props = {
  onClick: () => void;
  isOpen: boolean;
  expandState?: string;
};

const IncidentsBanner: React.FC<Props> = observer(
  ({onClick, isOpen, expandState}) => {
    const {processInstanceId} = useInstancePageParams();
    const {incidentsCount} = incidentsStore;

    const errorMessage = `There ${
      incidentsCount === 1 ? 'is' : 'are'
    } ${pluralSuffix(
      incidentsCount,
      'Incident'
    )} in Instance ${processInstanceId}`;
    const title = `View ${pluralSuffix(
      incidentsCount,
      'Incident'
    )} in Instance ${processInstanceId}`;

    if (expandState === EXPAND_STATE.COLLAPSED) {
      return null;
    }

    return (
      <Styled.IncidentsBanner
        data-testid="incidents-banner"
        onClick={onClick}
        title={title}
        isExpanded={isOpen}
        iconButtonTheme="incidentsBanner"
      >
        {errorMessage}
      </Styled.IncidentsBanner>
    );
  }
);

export {IncidentsBanner};
