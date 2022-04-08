/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Modal, Button} from 'components';
import {t} from 'translation';

export default function ConflictModal({conflict, onClose, onConfirm}) {
  const [loading, setLoading] = useState(false);

  const confirm = async () => {
    setLoading(true);
    await onConfirm();
    setLoading(false);
  };

  return (
    <Modal open={conflict} onClose={onClose} onConfirm={confirm} className="ConflictModal">
      <Modal.Header>{t('report.saveConflict.header')}</Modal.Header>
      <Modal.Content>
        {conflict &&
          ['combined_report', 'alert'].map((type) => {
            if (conflict[type].length === 0) {
              return null;
            }

            return (
              <div key={type}>
                <p>{t(`report.saveConflict.${type}.header`)}</p>
                <ul>
                  {conflict[type].map(({id, name}) => (
                    <li key={id}>'{name || id}'</li>
                  ))}
                </ul>
                <p>
                  <b>{t(`report.saveConflict.${type}.message`)}</b>
                </p>
              </div>
            );
          })}
      </Modal.Content>
      <Modal.Actions>
        <Button main disabled={loading} className="close" onClick={onClose}>
          {t('saveGuard.no')}
        </Button>
        <Button main disabled={loading} primary className="confirm" onClick={confirm}>
          {t('saveGuard.yes')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
