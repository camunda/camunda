/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useRef, useState} from 'react';
import {autorun} from 'mobx';
import {observer} from 'mobx-react';
import {useLocation, useNavigate} from 'react-router-dom';
import {Locations} from 'modules/routes';
import {DrdViewer} from 'modules/dmn-js/DrdViewer';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {drdStore} from 'modules/stores/drd';
import {drdDataStore} from 'modules/stores/drdData';
import {DecisionState} from './DecisionState';
import {Container, PanelHeader} from './styled';

const Drd: React.FC = observer(() => {
  const {
    setPanelState,
    state: {panelState},
  } = drdStore;
  const drdViewer = useRef<DrdViewer | null>(null);
  const drdViewerRef = useRef<HTMLDivElement | null>(null);
  const [definitionsName, setDefinitionsName] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();

  const handleDecisionSelection = (decisionId: string) => {
    const decisionInstances = drdDataStore.state.drdData?.[decisionId];

    if (decisionInstances === undefined) {
      return;
    }

    const decisionInstanceId =
      decisionInstances[decisionInstances.length - 1]?.decisionInstanceId;

    if (decisionInstanceId !== undefined) {
      navigate(Locations.decisionInstance(location, decisionInstanceId));
    }
  };

  if (drdViewer.current === null) {
    drdViewer.current = new DrdViewer(({name}) => {
      setDefinitionsName(name);
    }, handleDecisionSelection);
  }

  useEffect(() => {
    const disposer = autorun(() => {
      if (
        drdViewerRef.current !== null &&
        decisionXmlStore.state.xml !== null
      ) {
        drdViewer.current!.render(
          drdViewerRef.current,
          decisionXmlStore.state.xml,
          drdDataStore.selectableDecisions,
          drdDataStore.currentDecision,
          drdDataStore.decisionStates
        );
      }
    });
    return () => {
      disposer();
      drdViewer.current?.reset();
    };
  }, []);

  return (
    <Container data-testid="drd">
      <PanelHeader>
        <div>{definitionsName}</div>
        <div>
          {panelState === 'minimized' && (
            <button
              title="Maximize DRD Panel"
              onClick={() => setPanelState('maximized')}
            >
              Maximize
            </button>
          )}
          {panelState === 'maximized' && (
            <button
              title="Minimize DRD Panel"
              onClick={() => setPanelState('minimized')}
            >
              Minimize
            </button>
          )}
          <button
            title="Close DRD Panel"
            onClick={() => setPanelState('closed')}
          >
            X
          </button>
        </div>
      </PanelHeader>
      <div data-testid="drd-viewer" ref={drdViewerRef} />

      {drdDataStore.state.decisionStateOverlays.map(
        ({decisionId, state, container}) => (
          <DecisionState key={decisionId} state={state} container={container} />
        )
      )}
    </Container>
  );
});

export {Drd};
