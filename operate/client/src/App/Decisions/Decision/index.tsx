/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Restricted} from 'modules/components/Restricted';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useEffect, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {CopiableContent, PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {reaction} from 'mobx';
import {deleteSearchParams} from 'modules/utils/filter';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {notificationsStore} from 'modules/stores/notifications';

const Decision: React.FC = observer(() => {
  const location = useLocation();
  const navigate = useNavigate();

  const {
    state: {status},
    getDecisionName,
    getDecisionDefinitionId,
  } = groupedDecisionsStore;

  const params = new URLSearchParams(location.search);
  const version = params.get('version');
  const decisionId = params.get('name');
  const tenant = params.get('tenant');
  const [currentDecisionId, setCurrentDecisionId] = useState<string | null>(
    null,
  );

  const isDecisionSelected = decisionId !== null;
  const isVersionSelected = version !== null && version !== 'all';
  const decisionName =
    getDecisionName({decisionId, tenantId: tenant}) ?? 'Decision';

  const decisionDefinitionId =
    isDecisionSelected && isVersionSelected
      ? getDecisionDefinitionId({
          decisionId,
          tenantId: tenant,
          version: Number(version),
        })
      : null;

  useEffect(() => {
    if (status === 'fetched' && isDecisionSelected) {
      if (decisionDefinitionId === null) {
        decisionXmlStore.reset();

        if (
          !groupedDecisionsStore.isSelectedDecisionValid({
            decisionId,
            tenantId: tenant,
          })
        ) {
          navigate(deleteSearchParams(location, ['name', 'version']));
          notificationsStore.displayNotification({
            kind: 'error',
            title: 'Decision could not be found',
            isDismissable: true,
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
    tenant,
    location,
    navigate,
  ]);

  useEffect(() => {
    const disposer = reaction(
      () => decisionXmlStore.state.status,
      (status) => {
        if (status === 'fetched') {
          setCurrentDecisionId(decisionId);
        }
      },
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
    target.style['marginRight'] =
      `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const getStatus = () => {
    if (
      decisionXmlStore.state.status === 'fetching' ||
      decisionName === undefined ||
      !groupedDecisionsStore.isInitialLoadComplete ||
      (groupedDecisionsStore.state.status === 'fetching' &&
        location.state?.refreshContent)
    ) {
      return 'loading';
    }

    if (decisionXmlStore.state.status === 'error') {
      return 'error';
    }
    if (!isVersionSelected) {
      return 'empty';
    }

    return 'content';
  };

  return (
    <Section>
      <PanelHeader title={decisionName} ref={panelHeaderRef}>
        <>
          {decisionId !== null && (
            <CopiableContent
              copyButtonDescription="Decision ID / Click to copy"
              content={decisionId}
            />
          )}
          {isVersionSelected && decisionDefinitionId !== null && (
            <Restricted
              resourceBasedRestrictions={{
                scopes: ['DELETE'],
                permissions: groupedDecisionsStore.getPermissions(
                  decisionId ?? undefined,
                  tenant,
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
        </>
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
