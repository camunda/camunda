/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';

import {Modal, InstanceViewTable} from 'components';
import {t} from 'translation';

import './RawDataModal.scss';

export default function RawDataModal({name, report, open, onClose}) {
  return (
    <Modal className="RawDataModal" open={open} size="lg" onClose={onClose}>
      <Modal.Header title={name} />
      <Modal.Content>
        <InstanceViewTable report={report} />
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
