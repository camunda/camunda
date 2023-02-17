/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {autorun} from 'mobx';
import {observer} from 'mobx-react';
import {useNavigate} from 'react-router-dom';
import {Paths} from 'modules/routes';
import {DrdViewer} from 'modules/dmn-js/DrdViewer';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {drdStore} from 'modules/stores/drd';
import {drdDataStore} from 'modules/stores/drdData';
import {ReactComponent as Maximize} from 'modules/components/Icon/maximize.svg';
import {ReactComponent as Minimize} from 'modules/components/Icon/minimize.svg';
import {ReactComponent as Close} from 'modules/components/Icon/close.svg';
import {DecisionState} from './DecisionState';
import {Container, PanelHeader, ButtonContainer, Button} from './styled';
import {tracking} from 'modules/tracking';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';

const Drd: React.FC = observer(() => {
  const {
    setPanelState,
    state: {panelState},
  } = drdStore;
  const drdViewer = useRef<DrdViewer | null>(null);
  const drdViewerRef = useRef<HTMLDivElement | null>(null);
  const navigate = useNavigate();

  const handleDecisionSelection = (decisionId: string) => {
    const decisionInstances = drdDataStore.state.drdData?.[decisionId];

    if (decisionInstances === undefined) {
      return;
    }

    const decisionInstanceId =
      decisionInstances[decisionInstances.length - 1]?.decisionInstanceId;

    if (decisionInstanceId !== undefined) {
      navigate(Paths.decisionInstance(decisionInstanceId));
    }
  };

  if (drdViewer.current === null) {
    drdViewer.current = new DrdViewer(handleDecisionSelection);
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
        <div>{decisionDefinitionStore.name}</div>
        <ButtonContainer>
          {panelState === 'minimized' && (
            <Button
              title="Maximize DRD Panel"
              aria-label="Maximize DRD Panel"
              size="large"
              icon={<Maximize />}
              onClick={() => {
                setPanelState('maximized');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'maximize',
                });
              }}
            />
          )}
          {panelState === 'maximized' && (
            <Button
              title="Minimize DRD Panel"
              aria-label="Minimize DRD Panel"
              size="large"
              icon={<Minimize />}
              onClick={() => {
                setPanelState('minimized');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'minimize',
                });
              }}
            />
          )}
          <Button
            title="Close DRD Panel"
            aria-label="Close DRD Panel"
            size="large"
            icon={<Close />}
            onClick={() => {
              setPanelState('closed');
              tracking.track({
                eventName: 'drd-panel-interaction',
                action: 'close',
              });
            }}
          />
        </ButtonContainer>
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
