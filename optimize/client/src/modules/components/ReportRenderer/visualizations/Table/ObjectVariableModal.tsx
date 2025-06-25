/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {Modal, Loading} from 'components';
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
  const [objectString, setObjectString] = useState<string>();

  const updateObjectString = (objectString: string): void => {
    setObjectString(objectString ? objectString : t('report.disabledObjectVariables').toString());
  };

  useEffect(() => {
    mightFail(
      loadObjectValues(name, processInstanceId, processDefinitionKey, versions, tenantIds),
      updateObjectString,
      showError
    );
  }, [mightFail, name, processDefinitionKey, processInstanceId, tenantIds, versions]);

  return (
    <Modal className="ObjectVariableModal" open onClose={onClose}>
      <Modal.Header title={t('report.table.rawData.objectVariable')} />
      <Modal.Content>
        {objectString ? (
          <>
            <div>
              {t('report.table.rawData.variable')}: <b>{name}</b>
            </div>
            <pre>{objectString}</pre>
          </>
        ) : (
          <Loading />
        )}
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
