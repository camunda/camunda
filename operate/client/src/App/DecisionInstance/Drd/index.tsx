/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {observer} from 'mobx-react';
import {useNavigate} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {DrdViewer} from 'modules/dmn-js/DrdViewer';
import {PanelHeader, Container, Stack} from './styled';
import {tracking} from 'modules/tracking';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {Button} from '@carbon/react';
import {Close, Maximize, Minimize} from '@carbon/react/icons';
import {StateOverlay} from 'modules/components/StateOverlay';
import {useDecisionDefinitionXml} from 'modules/queries/decisionDefinitions/useDecisionDefinitionXml';
import {useDrdData} from 'modules/queries/decisionInstances/useDrdData';
import {useDrdStateOverlay} from 'modules/queries/decisionInstances/useDrdStateOverlay';
import type {DrdPanelState} from 'modules/queries/decisionInstances/useDrdPanelState';

type DrdProps = {
  decisionEvaluationInstanceKey: string;
  decisionDefinitionKey?: string;
  decisionEvaluationKey?: string;
  drdPanelState: DrdPanelState;
  onChangeDrdPanelState(state: DrdPanelState): void;
};

const Drd: React.FC<DrdProps> = observer((props) => {
  const drdViewer = useRef<DrdViewer | null>(null);
  const drdViewerRef = useRef<HTMLDivElement | null>(null);
  const navigate = useNavigate();

  const [overlayState, overlayActions] = useDrdStateOverlay();
  const {data: drdData} = useDrdData(props.decisionEvaluationKey);
  const {data: decisionDefinitionXml} = useDecisionDefinitionXml({
    decisionDefinitionKey: props.decisionDefinitionKey,
  });

  if (drdViewer.current === null) {
    const handleDecisionSelection = (decisionEvaluationInstanceKey: string) => {
      navigate(Paths.decisionInstance(decisionEvaluationInstanceKey));
    };
    drdViewer.current = new DrdViewer(handleDecisionSelection);
  }

  useEffect(() => {
    if (
      drdViewerRef.current !== null &&
      drdData !== undefined &&
      decisionDefinitionXml !== undefined
    ) {
      drdViewer.current!.render(
        props.decisionEvaluationInstanceKey,
        drdViewerRef.current,
        drdData,
        decisionDefinitionXml,
        overlayActions,
      );
    }

    return () => {
      drdViewer.current?.reset();
    };
  }, [
    props.decisionEvaluationInstanceKey,
    decisionDefinitionXml,
    drdData,
    overlayActions,
  ]);

  return (
    <Container data-testid="drd">
      <PanelHeader title={decisionDefinitionStore.name ?? ''}>
        <Stack orientation="horizontal">
          {props.drdPanelState === 'minimized' && (
            <Button
              kind="ghost"
              hasIconOnly
              renderIcon={Maximize}
              tooltipPosition="left"
              iconDescription="Maximize DRD Panel"
              aria-label="Maximize DRD Panel"
              size="lg"
              onClick={() => {
                props.onChangeDrdPanelState('maximized');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'maximize',
                });
              }}
            />
          )}
          {props.drdPanelState === 'maximized' && (
            <Button
              kind="ghost"
              hasIconOnly
              renderIcon={Minimize}
              tooltipPosition="left"
              iconDescription="Minimize DRD Panel"
              aria-label="Minimize DRD Panel"
              size="lg"
              onClick={() => {
                props.onChangeDrdPanelState('minimized');
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
              props.onChangeDrdPanelState('closed');
              tracking.track({
                eventName: 'drd-panel-interaction',
                action: 'close',
              });
            }}
          />
        </Stack>
      </PanelHeader>
      <div data-testid="drd-viewer" ref={drdViewerRef} />

      {overlayState.map((overlay) => (
        <StateOverlay
          key={overlay.decisionDefinitionId}
          state={overlay.state}
          container={overlay.container}
        />
      ))}
    </Container>
  );
});

export {Drd};
