/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {reaction} from 'mobx';
import {observer} from 'mobx-react';
import {useLocation, useNavigate} from 'react-router-dom';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {StatusMessage} from 'modules/components/StatusMessage';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {Container, PanelHeader} from './styled';
import {deleteSearchParams} from 'modules/utils/filter';
import {useNotifications} from 'modules/notifications';
import {IS_DECISION_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {Restricted} from 'modules/components/Restricted';
import {DecisionOperations} from './DecisionOperations';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/components/CollapsablePanel/styled';

const Decision: React.FC = observer(() => {
  const location = useLocation();
  const navigate = useNavigate();
  const notifications = useNotifications();

  const {
    state: {status, decisions},
    getDecisionName,
    getDecisionDefinitionId,
  } = groupedDecisionsStore;
  const params = new URLSearchParams(location.search);
  const version = params.get('version');
  const decisionId = params.get('name');
  const [currentDecisionId, setCurrentDecisionId] = useState<string | null>(
    null
  );
  const isDecisionSelected = decisionId !== null;
  const isVersionSelected = version !== null && version !== 'all';
  const decisionName = getDecisionName(decisionId);
  const decisionDefinitionId =
    isDecisionSelected && isVersionSelected
      ? getDecisionDefinitionId({
          decisionId,
          version: Number(version),
        })
      : null;

  useEffect(() => {
    if (status === 'fetched' && isDecisionSelected) {
      if (decisionDefinitionId === null) {
        decisionXmlStore.reset();

        if (
          !groupedDecisionsStore.isSelectedDecisionValid(decisions, decisionId)
        ) {
          navigate(deleteSearchParams(location, ['name', 'version']));
          notifications.displayNotification('error', {
            headline: 'Decision could not be found',
          });
        }
      } else {
        decisionXmlStore.fetchDiagramXml(decisionDefinitionId);
      }
    }
  }, [
    decisionDefinitionId,
    isDecisionSelected,
    status,
    decisionId,
    notifications,
    location,
    navigate,
    decisions,
  ]);

  useEffect(() => {
    const disposer = reaction(
      () => decisionXmlStore.state.status,
      (status) => {
        if (status === 'fetched') {
          setCurrentDecisionId(decisionId);
        }
      }
    );

    return () => {
      disposer();
    };
  }, [decisionId]);

  useEffect(() => {
    return decisionXmlStore.reset;
  }, []);

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style['marginRight'] = `${width - COLLAPSABLE_PANEL_MIN_WIDTH}px`;
  });

  return (
    <Container>
      <PanelHeader title={decisionName || 'Decision'} ref={panelHeaderRef}>
        {IS_DECISION_DEFINITION_DELETION_ENABLED &&
          isVersionSelected &&
          decisionDefinitionId !== null && (
            <Restricted scopes={['write']}>
              <DecisionOperations
                decisionDefinitionId={decisionDefinitionId}
                decisionName={decisionName || 'Decision'}
                decisionVersion={version}
              />
            </Restricted>
          )}
      </PanelHeader>
      {(() => {
        if (decisionXmlStore.state.status === 'error') {
          return (
            <EmptyMessage
              message={
                <StatusMessage variant="error">
                  Data could not be fetched
                </StatusMessage>
              }
            />
          );
        }

        if (!isDecisionSelected) {
          return (
            <EmptyMessage
              message={`There is no Decision selected
                To see a Decision Table or a Literal Expression, select a Decision in the Filters panel`}
            />
          );
        }

        if (!isVersionSelected && decisionName !== undefined) {
          return (
            <EmptyMessage
              message={`There is more than one Version selected for Decision "${getDecisionName(
                decisionId
              )}"
                To see a Decision Table or a Literal Expression, select a single Version`}
            />
          );
        }

        return (
          <>
            {(decisionXmlStore.state.status === 'fetching' ||
              decisionName === undefined) && <SpinnerSkeleton />}

            {isDecisionSelected && isVersionSelected && (
              <DecisionViewer
                xml={decisionXmlStore.state.xml}
                decisionViewId={currentDecisionId}
              />
            )}
          </>
        );
      })()}
    </Container>
  );
});

export {Decision};
