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

import {useState} from 'react';
import {Stack} from '@carbon/react';
import isNil from 'lodash/isNil';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {Header} from '../Header';
import {SummaryDataKey, SummaryDataValue} from '../styled';
import {getExecutionDuration} from './getExecutionDuration';
import {buildMetadata} from './buildMetadata';

type Props = {
  metaData: MetaDataDto;
  flowNodeId: string;
};

const NULL_METADATA = {
  flowNodeInstanceId: null,
  startDate: null,
  endDate: null,
  calledProcessInstanceId: null,
  calledProcessDefinitionName: null,
  calledDecisionInstanceId: null,
  calledDecisionDefinitionName: null,
  flowNodeType: null,
  jobRetries: null,
} as const;

const Details: React.FC<Props> = ({metaData, flowNodeId}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const businessObject =
    processInstanceDetailsDiagramStore.businessObjects[flowNodeId];
  const flowNodeName = businessObject?.name || flowNodeId;
  const {instanceMetadata, incident} = metaData;
  const {
    flowNodeInstanceId,
    startDate,
    endDate,
    calledProcessInstanceId,
    calledProcessDefinitionName,
    calledDecisionInstanceId,
    calledDecisionDefinitionName,
    flowNodeType,
    jobRetries,
  } = instanceMetadata ?? NULL_METADATA;

  return (
    <>
      <Header
        title="Details"
        link={
          !isNil(window.clientConfig?.tasklistUrl) &&
          flowNodeType === 'USER_TASK'
            ? {
                href: window.clientConfig!.tasklistUrl,
                label: 'Open Tasklist',
                onClick: () => {
                  tracking.track({
                    eventName: 'open-tasklist-link-clicked',
                  });
                },
              }
            : undefined
        }
        button={{
          title: 'Show more metadata',
          label: 'View',
          onClick: () => {
            setIsModalVisible(true);
            tracking.track({
              eventName: 'flow-node-instance-details-opened',
            });
          },
        }}
      />
      <Stack gap={5}>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Flow Node Instance Key</SummaryDataKey>
          <SummaryDataValue>{flowNodeInstanceId}</SummaryDataValue>
        </Stack>
        <Stack gap={3} as="dl">
          <SummaryDataKey>Execution Duration</SummaryDataKey>
          <SummaryDataValue>
            {getExecutionDuration(startDate!, endDate)}
          </SummaryDataValue>
        </Stack>

        {jobRetries !== null && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Retries Left</SummaryDataKey>
            <SummaryDataValue data-testid="retries-left-count">
              {jobRetries}
            </SummaryDataValue>
          </Stack>
        )}

        {businessObject?.$type === 'bpmn:CallActivity' &&
          flowNodeType !== 'MULTI_INSTANCE_BODY' && (
            <Stack gap={3} as="dl">
              <SummaryDataKey>Called Process Instance</SummaryDataKey>
              <SummaryDataValue data-testid="called-process-instance">
                {calledProcessInstanceId ? (
                  <Link
                    to={Paths.processInstance(calledProcessInstanceId)}
                    title={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                    aria-label={`View ${calledProcessDefinitionName} instance ${calledProcessInstanceId}`}
                  >
                    {`${calledProcessDefinitionName} - ${calledProcessInstanceId}`}
                  </Link>
                ) : (
                  'None'
                )}
              </SummaryDataValue>
            </Stack>
          )}

        {businessObject?.$type === 'bpmn:BusinessRuleTask' && (
          <Stack gap={3} as="dl">
            <SummaryDataKey>Called Decision Instance</SummaryDataKey>
            <SummaryDataValue>
              {calledDecisionInstanceId ? (
                <Link
                  to={Paths.decisionInstance(calledDecisionInstanceId)}
                  title={`View ${calledDecisionDefinitionName} instance ${calledDecisionInstanceId}`}
                  aria-label={`View ${calledDecisionDefinitionName} instance ${calledDecisionInstanceId}`}
                >
                  {`${calledDecisionDefinitionName} - ${calledDecisionInstanceId}`}
                </Link>
              ) : (
                calledDecisionDefinitionName ?? '—'
              )}
            </SummaryDataValue>
          </Stack>
        )}
      </Stack>
      <JSONEditorModal
        isVisible={isModalVisible}
        onClose={() => setIsModalVisible(false)}
        title={`Flow Node "${flowNodeName}" ${flowNodeInstanceId} Metadata`}
        value={buildMetadata(instanceMetadata, incident)}
        readOnly
      />
    </>
  );
};

export {Details};
