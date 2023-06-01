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
import {useRef} from 'react';
import {useLocation} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/Carbon/DiagramShell';

const Decision: React.FC = observer(() => {
  const location = useLocation();

  const {getDecisionName, getDecisionDefinitionId} = groupedDecisionsStore;

  const params = new URLSearchParams(location.search);
  const version = params.get('version');
  const decisionId = params.get('name');

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

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style[
      'marginRight'
    ] = `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  return (
    <Section>
      <PanelHeader title="Decision" ref={panelHeaderRef}>
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
        status={isVersionSelected ? 'content' : 'empty'}
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
        errorMessage={{
          message: 'Data could not be fetched',
          additionalInfo: 'Refresh the page to try again',
        }}
      >
        decisions - diagram
      </DiagramShell>
    </Section>
  );
});

export {Decision};
