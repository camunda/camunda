/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {Modal, LoadingIndicator} from 'components';
import {withErrorHandling, WithErrorHandlingProps} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadObjectValues} from './service';

import './ObjectVariableModal.scss';

interface ObjectVariableModalProps extends WithErrorHandlingProps {
  variable: {
    name: string;
    processInstanceId: string;
    processDefinitionKey: string;
    versions: string[];
    tenantIds: (string | null)[];
  };
  onClose?: () => void;
}

export function ObjectVariableModal({
  variable: {name, processInstanceId, processDefinitionKey, versions, tenantIds},
  onClose,
  mightFail,
}: ObjectVariableModalProps) {
  const [objectString, setObjectString] = useState();

  useEffect(() => {
    mightFail(
      loadObjectValues(name, processInstanceId, processDefinitionKey, versions, tenantIds),
      setObjectString,
      showError
    );
  }, [mightFail, name, processDefinitionKey, processInstanceId, tenantIds, versions]);

  return (
    <Modal className="ObjectVariableModal" open onClose={onClose}>
      <Modal.Header>{t('report.table.rawData.objectVariable')}</Modal.Header>
      <Modal.Content>
        <div>
          {t('report.table.rawData.variable')}: <b>{name}</b>
        </div>
        <pre>{objectString || <LoadingIndicator />}</pre>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(ObjectVariableModal);
