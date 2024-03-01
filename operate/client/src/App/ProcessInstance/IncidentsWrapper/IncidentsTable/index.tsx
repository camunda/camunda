/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {IncidentOperation} from 'modules/components/IncidentOperation';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {sortIncidents} from './service';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {FlexContainer, ErrorMessageCell} from './styled';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {Incident, incidentsStore} from 'modules/stores/incidents';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {useLocation} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Button} from '@carbon/react';
import {SortableTable} from 'modules/components/SortableTable';
import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';

const IncidentsTable: React.FC = observer(function IncidentsTable() {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [modalContent, setModalContent] = useState<string>('');
  const [modalTitle, setModalTitle] = useState<string>('');

  const {processInstanceId = ''} = useProcessInstancePageParams();
  const location = useLocation();
  const {sortBy, sortOrder} = getSortParams(location.search) || {
    sortBy: 'creationTime',
    sortOrder: 'desc',
  };

  const {filteredIncidents} = incidentsStore;

  const handleModalClose = () => {
    setIsModalVisible(false);
    setModalContent('');
    setModalTitle('');
  };

  const handleMoreButtonClick = (
    errorMessage: string,
    flowNodeName: string,
  ) => {
    setIsModalVisible(true);
    setModalContent(errorMessage);
    setModalTitle(`Flow Node "${flowNodeName}" Error`);
  };

  const sortedIncidents: Incident[] = sortIncidents(
    filteredIncidents,
    sortBy,
    sortOrder,
  );

  const isJobIdPresent = sortedIncidents.some(({jobId}) => jobId !== null);
  const hasPermissionForRetryOperation =
    authenticationStore.hasPermission(['write']) &&
    processInstanceDetailsStore.hasPermission(['UPDATE_PROCESS_INSTANCE']);

  return (
    <>
      <SortableTable
        state="content"
        selectionType="row"
        columnsWithNoContentPadding={['operations', 'errorMessage']}
        onSelect={(rowId) => {
          const incident = sortedIncidents.find(({id}) => id === rowId);
          if (incident === undefined) {
            return;
          }

          incidentsStore.isSingleIncidentSelected(incident.flowNodeInstanceId)
            ? flowNodeSelectionStore.clearSelection()
            : flowNodeSelectionStore.selectFlowNode({
                flowNodeId: incident.flowNodeId,
                flowNodeInstanceId: incident.flowNodeInstanceId,
                isMultiInstance: false,
              });
        }}
        checkIsRowSelected={(rowId) => {
          const incident = sortedIncidents.find(({id}) => id === rowId);
          if (incident === undefined) {
            return false;
          }
          return incident.isSelected;
        }}
        onSort={(sortKey: string) => {
          tracking.track({
            eventName: 'incidents-sorted',
            column: sortKey,
          });
        }}
        headerColumns={[
          {
            header: 'Incident Type',
            key: 'errorType',
          },
          {
            header: 'Failing Flow Node',
            key: 'flowNodeName',
          },
          {
            header: 'Job Id',
            key: 'jobId',
            isDisabled: !isJobIdPresent,
          },
          {
            header: 'Creation Date',
            key: 'creationTime',
            isDefault: true,
          },
          {
            header: 'Error Message',
            key: 'errorMessage',
            isDisabled: true,
          },
          ...(processInstanceDetailsDiagramStore.hasCalledProcessInstances
            ? [
                {
                  header: 'Root Cause Instance',
                  key: 'rootCauseInstance',
                },
              ]
            : []),
          ...(hasPermissionForRetryOperation
            ? [
                {
                  header: 'Operations',
                  key: 'operations',
                  isDisabled: true,
                },
              ]
            : []),
        ]}
        rows={sortedIncidents.map((incident) => {
          const {rootCauseInstance} = incident;
          const areOperationsVisible =
            rootCauseInstance === null ||
            rootCauseInstance.instanceId === processInstanceId;

          return {
            id: incident.id,
            errorType: incident.errorType.name,
            flowNodeName: incident.flowNodeName,
            jobId: incident.jobId || '--',
            creationTime: formatDate(incident.creationTime),
            errorMessage: (
              <FlexContainer>
                <ErrorMessageCell>{incident.errorMessage}</ErrorMessageCell>
                {incident.errorMessage.length >= 58 && (
                  <Button
                    size="sm"
                    kind="ghost"
                    onClick={(event) => {
                      event.stopPropagation();

                      tracking.track({
                        eventName: 'incidents-panel-full-error-message-opened',
                      });

                      handleMoreButtonClick(
                        incident.errorMessage,
                        incident.flowNodeName,
                      );
                    }}
                  >
                    More
                  </Button>
                )}
              </FlexContainer>
            ),

            rootCauseInstance:
              processInstanceDetailsDiagramStore.hasCalledProcessInstances &&
              rootCauseInstance !== null ? (
                rootCauseInstance.instanceId === processInstanceId ? (
                  '--'
                ) : (
                  <Link
                    to={{
                      pathname: Paths.processInstance(
                        rootCauseInstance.instanceId,
                      ),
                    }}
                    title={`View root cause instance ${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                  >
                    {`${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`}
                  </Link>
                )
              ) : undefined,

            operations:
              hasPermissionForRetryOperation && areOperationsVisible ? (
                <IncidentOperation
                  instanceId={processInstanceId}
                  incident={incident}
                  showSpinner={incident.hasActiveOperation}
                />
              ) : undefined,
          };
        })}
      />
      <JSONEditorModal
        isVisible={isModalVisible}
        title={modalTitle}
        value={modalContent}
        onClose={handleModalClose}
        readOnly
      />
    </>
  );
});

export {IncidentsTable};
