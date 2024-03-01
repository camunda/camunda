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

import {useEffect, useState} from 'react';
import {DangerButton} from 'modules/components/OperationItem/DangerButton';
import {OperationItems} from 'modules/components/OperationItems';
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {operationsStore} from 'modules/stores/operations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {StructuredList} from 'modules/components/StructuredList';
import {UnorderedList} from 'modules/components/DeleteDefinitionModal/Warning/styled';
import {notificationsStore} from 'modules/stores/notifications';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {processInstancesStore} from 'modules/stores/processInstances';

type Props = {
  processDefinitionId: string;
  processName: string;
  processVersion: string;
};

const ProcessOperations: React.FC<Props> = observer(
  ({processDefinitionId, processName, processVersion}) => {
    const [isDeleteModalVisible, setIsDeleteModalVisible] =
      useState<boolean>(false);

    const [isOperationRunning, setIsOperationRunning] = useState(false);
    const {runningInstancesCount} = processInstancesStore.state;

    useEffect(() => {
      processInstancesStore.fetchRunningInstancesCount();

      return () => {
        setIsOperationRunning(false);
        processInstancesStore.setRunningInstancesCount(-1);
      };
    }, [processDefinitionId]);

    return (
      <>
        <DeleteButtonContainer>
          {isOperationRunning && (
            <InlineLoading data-testid="delete-operation-spinner" />
          )}
          <OperationItems>
            <DangerButton
              title={
                runningInstancesCount > 0
                  ? 'Only process definitions without running instances can be deleted.'
                  : `Delete Process Definition "${processName} - Version ${processVersion}"`
              }
              type="DELETE"
              disabled={isOperationRunning || runningInstancesCount !== 0}
              onClick={() => {
                tracking.track({
                  eventName: 'definition-deletion-button',
                  resource: 'process',
                  version: processVersion,
                });

                setIsDeleteModalVisible(true);
              }}
            />
          </OperationItems>
        </DeleteButtonContainer>
        <DeleteDefinitionModal
          title="Delete Process Definition"
          description="You are about to delete the following process definition:"
          confirmationText="Yes, I confirm I want to delete this process definition."
          isVisible={isDeleteModalVisible}
          warningTitle="Deleting a process definition will permanently remove it and will
        impact the following:"
          warningContent={
            <Stack gap={6}>
              <UnorderedList nested>
                <ListItem>
                  All the deleted process definition’s finished process
                  instances will be deleted from the application.
                </ListItem>
                <ListItem>
                  All decision and process instances referenced by the deleted
                  process instances will be deleted.
                </ListItem>
                <ListItem>
                  If a process definition contains user tasks, they will be
                  deleted from Tasklist.
                </ListItem>
              </UnorderedList>
              <Link
                href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
                target="_blank"
              >
                For a detailed overview, please view our guide on deleting a
                process definition
              </Link>
            </Stack>
          }
          bodyContent={
            <StructuredList
              headerColumns={[
                {
                  cellContent: 'Process Definition',
                },
              ]}
              rows={[
                {
                  key: `${processName}-v${processVersion}`,
                  columns: [
                    {cellContent: `${processName} - Version ${processVersion}`},
                  ],
                },
              ]}
              label="Process Details"
            />
          }
          onClose={() => setIsDeleteModalVisible(false)}
          onDelete={() => {
            setIsOperationRunning(true);
            setIsDeleteModalVisible(false);

            tracking.track({
              eventName: 'definition-deletion-confirmation',
              resource: 'process',
              version: processVersion,
            });

            operationsStore.applyDeleteProcessDefinitionOperation({
              processDefinitionId,
              onSuccess: () => {
                setIsOperationRunning(false);
                panelStatesStore.expandOperationsPanel();

                notificationsStore.displayNotification({
                  kind: 'success',
                  title: 'Operation created',
                  isDismissable: true,
                });
              },
              onError: (statusCode: number) => {
                setIsOperationRunning(false);

                notificationsStore.displayNotification({
                  kind: 'error',
                  title: 'Operation could not be created',
                  subtitle:
                    statusCode === 403
                      ? 'You do not have permission'
                      : undefined,
                  isDismissable: true,
                });
              },
            });
          }}
        />
      </>
    );
  },
);

export {ProcessOperations};
