/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {autorun} from 'mobx';
import {observer} from 'mobx-react';
import {useNavigate} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {DrdViewer} from 'modules/dmn-js/DrdViewer';
import {drdStore} from 'modules/stores/drd';
import {drdDataStore} from 'modules/stores/drdData';
import {PanelHeader, Container, Stack} from './styled';
import {tracking} from 'modules/tracking';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {Button} from '@carbon/react';
import {Close, Maximize, Minimize} from '@carbon/react/icons';
import {StateOverlay} from 'modules/components/StateOverlay';
import {useQuery} from '@tanstack/react-query';
import {useDecisionDefinitionXmlOptions} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';

const Drd: React.FC<{decisionDefinitionKey?: string}> = observer(
  ({decisionDefinitionKey}) => {
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

    const {data: decisionDefinitionXml} = useQuery(
      useDecisionDefinitionXmlOptions({
        decisionDefinitionKey,
        enabled: !!decisionDefinitionKey,
      }),
    );

    useEffect(() => {
      const disposer = autorun(() => {
        if (
          drdViewerRef.current !== null &&
          decisionDefinitionXml !== undefined
        ) {
          drdViewer.current!.render(
            drdViewerRef.current,
            decisionDefinitionXml,
            drdDataStore.selectableDecisions,
            drdDataStore.currentDecision,
            drdDataStore.decisionStates,
          );
        }
      });
      return () => {
        disposer();
        drdViewer.current?.reset();
      };
    }, [decisionDefinitionXml]);

    return (
      <Container data-testid="drd">
        <PanelHeader title={decisionDefinitionStore.name ?? ''}>
          <Stack orientation="horizontal">
            {panelState === 'minimized' && (
              <Button
                kind="ghost"
                hasIconOnly
                renderIcon={Maximize}
                tooltipPosition="left"
                iconDescription="Maximize DRD Panel"
                aria-label="Maximize DRD Panel"
                size="lg"
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
                kind="ghost"
                hasIconOnly
                renderIcon={Minimize}
                tooltipPosition="left"
                iconDescription="Minimize DRD Panel"
                aria-label="Minimize DRD Panel"
                size="lg"
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
              kind="ghost"
              hasIconOnly
              renderIcon={Close}
              tooltipPosition="left"
              iconDescription="Close DRD Panel"
              aria-label="Close DRD Panel"
              size="lg"
              onClick={() => {
                setPanelState('closed');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'close',
                });
              }}
            />
          </Stack>
        </PanelHeader>
        <div data-testid="drd-viewer" ref={drdViewerRef} />

        {drdDataStore.state.decisionStateOverlays.map(
          ({decisionId, state, container}) => (
            <StateOverlay
              key={decisionId}
              state={state}
              container={container}
            />
          ),
        )}
      </Container>
    );
  },
);

export {Drd};
