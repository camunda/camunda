/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {IncidentsBanner} from './IncidentsBanner';
import IncidentsOverlay from './IncidentsOverlay';
import {IncidentsTable} from './IncidentsTable';
import {IncidentsFilter} from './IncidentsFilter';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';

import * as Styled from './styled';

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
  isOpen: boolean;
  onClick?: () => void;
};

const IncidentsWrapper: React.FC<Props> = observer(function (props) {
  const {expandState, isOpen, onClick} = props;
  const [isInTransition, setIsInTransition] = useState(false);

  useEffect(() => {
    incidentsStore.init();

    return () => {
      incidentsStore.reset();
    };
  }, []);

  function handleToggle() {
    !isInTransition && onClick?.();
  }

  if (incidentsStore.incidentsCount === 0) {
    return null;
  }

  return (
    <>
      <IncidentsBanner
        onClick={handleToggle}
        isOpen={isOpen}
        expandState={expandState}
      />
      <Styled.Transition
        in={isOpen}
        onEnter={() => setIsInTransition(true)}
        onEntered={() => setIsInTransition(false)}
        onExit={() => setIsInTransition(true)}
        onExited={() => setIsInTransition(false)}
        mountOnEnter
        unmountOnExit
        timeout={400}
      >
        <IncidentsOverlay>
          <IncidentsFilter />
          <IncidentsTable />
        </IncidentsOverlay>
      </Styled.Transition>
    </>
  );
});

export {IncidentsWrapper};
