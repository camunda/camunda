/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {observer} from 'mobx-react';
import {Restricted} from 'modules/components/Restricted';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useEffect, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {DecisionOperations} from './DecisionOperations';
import {PanelHeader, Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {reaction} from 'mobx';
import {deleteSearchParams} from 'modules/utils/filter';
import {DecisionViewer} from 'modules/components/DecisionViewer';
import {notificationsStore} from 'modules/stores/notifications';
import {CopiableContent} from 'modules/components/PanelHeader/CopiableContent';

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
              scopes={['write']}
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
