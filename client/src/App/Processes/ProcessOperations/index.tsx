/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {OperationItem} from 'modules/components/OperationItem';
import {OperationItems} from 'modules/components/OperationItems';
import {DeleteDefinitionModal} from 'modules/components/DeleteDefinitionModal';
import {DetailTable} from 'modules/components/DeleteDefinitionModal/DetailTable';
import {Warning, Information, Ul, Link} from './styled';

type Props = {
  processName: string;
  processVersion: string;
};

const ProcessOperations: React.FC<Props> = ({processName, processVersion}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  return (
    <>
      <OperationItems>
        <OperationItem
          title={`Delete Process Definition "${processName} - Version ${processVersion}"`}
          type="DELETE"
          onClick={() => setIsDeleteModalVisible(true)}
        />
      </OperationItems>
      <DeleteDefinitionModal
        title="Delete Process Definition"
        description="You are about to delete the following process definition:"
        isVisible={isDeleteModalVisible}
        bodyContent={
          <DetailTable
            headerColumns={[
              {
                cellContent: 'Process Definition',
              },
            ]}
            rows={[
              {
                columns: [
                  {cellContent: `${processName} - Version ${processVersion}`},
                ],
              },
            ]}
          />
        }
        warningContent={
          <Warning>
            <Information>
              Deleting a process definition will permanently remove it and will
              impact the following:
            </Information>
            <Ul>
              <li>
                All the deleted process definition’s running process instances
                will be immediately canceled and deleted.
              </li>
              <li>
                All the deleted process definition’s finished process instances
                will be deleted from the application.
              </li>
              <li>
                All decision and process instances referenced by the deleted
                process instances will be deleted.
              </li>
              <li>
                If a process definition contains user tasks, they will be
                canceled and deleted from Tasklist.
              </li>
            </Ul>
            <Link
              href="https://docs.camunda.io/docs/components/operate/operate-introduction/"
              target="_blank"
            >
              For a detailed overview, please view our guide on deleting a
              process definition
            </Link>
          </Warning>
        }
        confirmationText="Yes, I confirm I want to delete this process definition."
        onClose={() => setIsDeleteModalVisible(false)}
        onDelete={() => {}}
      />
    </>
  );
};

export {ProcessOperations};
