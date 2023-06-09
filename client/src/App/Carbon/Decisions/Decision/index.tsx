/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {Restricted} from 'modules/components/Restricted';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {IS_DECISION_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useEffect, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/Carbon/DiagramShell';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {reaction} from 'mobx';
import {deleteSearchParams} from 'modules/utils/filter';
import {useNotifications} from 'modules/notifications';
import {DecisionViewer} from 'modules/components/Carbon/DecisionViewer';

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
  const decisionName = getDecisionName(decisionId) ?? 'Decision';

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
    target.style[
      'marginRight'
    ] = `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const getStatus = () => {
    if (decisionXmlStore.state.status === 'error') {
      return 'error';
    }
    if (!isVersionSelected) {
      return 'empty';
    }
    if (
      decisionXmlStore.state.status === 'fetching' ||
      decisionName === undefined
    ) {
      return 'loading';
    }
    return 'content';
  };

  return (
    <Section>
      <PanelHeader title={decisionName} ref={panelHeaderRef}>
        {IS_DECISION_DEFINITION_DELETION_ENABLED &&
          isVersionSelected &&
          decisionDefinitionId !== null && (
            <Restricted
              scopes={['write']}
              resourceBasedRestrictions={{
                scopes: ['DELETE'],
                permissions: groupedDecisionsStore.getPermissions(
                  decisionId ?? undefined
                ),
              }}
            >
              <DecisionOperations
                decisionDefinitionId={decisionDefinitionId}
                decisionName={decisionName}
                decisionVersion={version}
              />
            </Restricted>
          )}
      </PanelHeader>
      <DiagramShell
        status={getStatus()}
        emptyMessage={
          version === 'all'
            ? {
                message: `There is more than one Version selected for Decision "${decisionName}"`,
                additionalInfo:
                  'To see a Decision Table or a Literal Expression, select a single Version',
              }
            : {
                message: 'There is no Decision selected',
                additionalInfo:
                  'To see a Decision Table or a Literal Expression, select a Decision in the Filters panel',
              }
        }
      >
        <DecisionViewer
          xml={decisionXmlStore.state.xml}
          decisionViewId={currentDecisionId}
        />
      </DiagramShell>
    </Section>
  );
});

export {Decision};
