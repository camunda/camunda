/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
