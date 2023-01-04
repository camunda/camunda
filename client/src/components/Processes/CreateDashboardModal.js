/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Button, Modal} from 'components';
import {t} from 'translation';

export default function CreateDashboardModal({onClose, onConfirm, linkToDashboard}) {
  return (
    <Modal open onClose={onClose} className="CreateDashboardModal">
      <Modal.Header>{t('processes.createDefaultDashboard')}</Modal.Header>
      <Modal.Content>{t('processes.createDashboardMessage')}</Modal.Content>
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Link className="Button primary main" to={linkToDashboard} onClick={onConfirm}>
          {t('dashboard.create')}
        </Link>
      </Modal.Actions>
    </Modal>
  );
}
