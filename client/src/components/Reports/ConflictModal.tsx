/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';

interface ConflictModalProps {
  conflict?: Record<string, {id: string; name: string}[]>;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export default function ConflictModal({
  conflict,
  onClose,
  onConfirm,
}: ConflictModalProps): JSX.Element {
  const [loading, setLoading] = useState<boolean>(false);

  const confirm = async (): Promise<void> => {
    setLoading(true);
    await onConfirm();
    setLoading(false);
  };

  return (
    <Modal open={!!conflict} onClose={onClose} className="ConflictModal">
      <Modal.Header>{t('report.saveConflict.header')}</Modal.Header>
      <Modal.Content>
        {conflict &&
          ['combined_report', 'alert'].map((type) => {
            if (conflict[type]?.length === 0) {
              return null;
            }

            return (
              <div key={type}>
                <p>{t(`report.saveConflict.${type}.header`)}</p>
                <ul>
                  {conflict[type]?.map(({id, name}: {id: string; name: string}) => (
                    <li key={id}>'{name || id}'</li>
                  ))}
                </ul>
                <p className="conflictMessage">
                  <b>{t(`report.saveConflict.${type}.message`)}</b>
                </p>
              </div>
            );
          })}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" disabled={loading} className="close" onClick={onClose}>
          {t('saveGuard.no')}
        </Button>
        <Button disabled={loading} className="confirm" onClick={confirm}>
          {t('saveGuard.yes')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
