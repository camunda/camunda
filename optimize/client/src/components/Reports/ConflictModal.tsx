/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      <Modal.Header title={t('report.saveConflict.header')} />
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
