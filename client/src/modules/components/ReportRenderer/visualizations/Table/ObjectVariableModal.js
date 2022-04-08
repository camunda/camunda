/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Modal, Button, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadObjectValues} from './service';

import './ObjectVariableModal.scss';

export function ObjectVariableModal({
  variable: {name, processInstanceId, processDefinitionKey, versions, tenantIds},
  onClose,
  mightFail,
}) {
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
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(ObjectVariableModal);
