/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode, useState} from 'react';
import {useHistory} from 'react-router-dom';
import {Button, Form, TextInput} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';
import {showError} from 'notifications';
import {addSources} from 'services';
import {useErrorHandling} from 'hooks';
import {Source} from 'types';

import SourcesModal from './SourcesModal';

import './CollectionModal.scss';

interface CollectionModalProps {
  onClose: () => void;
  title?: ReactNode;
  confirmText: string;
  initialName?: string;
  onConfirm: (name?: string) => Promise<string>;
  showSourcesModal?: boolean;
}

export function CollectionModal({
  onClose,
  title,
  confirmText,
  initialName,
  onConfirm,
  showSourcesModal,
}: CollectionModalProps) {
  const [name, setName] = useState(initialName);
  const [loading, setLoading] = useState(false);
  const [redirect, setRedirect] = useState<string | null>(null);
  const [displaySourcesModal, setDisplaySourcesModal] = useState(false);
  const {mightFail} = useErrorHandling();
  const history = useHistory();
  
  const confirm = () => {
    if (!name || loading) {
      return;
    }

    if (showSourcesModal) {
      setDisplaySourcesModal(true);
    } else {
      performRequest();
    }
  };

  const performRequest = (sources?: Source[]) => {
    mightFail(
      onConfirm(name),
      (id) => {
        if (id && sources) {
          mightFail(
            addSources(id, sources),
            () => {
              setRedirect(id);
            },
            showError,
            () => setLoading(false)
          );
        }
      },
      (error) => {
        showError(error);
      },
      () => setLoading(false)
    );
  };

  if (redirect) {
    history.push(`/collection/${redirect}/`);
  }

  return (
    <>
      <Modal className="CollectionModal" open={!displaySourcesModal} onClose={onClose}>
        <Modal.Header title={title} />
        <Modal.Content>
          <Form>
            {showSourcesModal && <div className="info">{t('common.collection.modal.info')}</div>}
            <TextInput
              id="collectionName"
              labelText={t('common.collection.modal.inputLabel')}
              value={name}
              onChange={({target: {value}}) => setName(value)}
              disabled={loading}
              autoComplete="off"
              data-modal-primary-focus
            />
          </Form>
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={onClose} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" disabled={!name || loading} onClick={confirm}>
            {showSourcesModal ? t('common.collection.modal.addDataSources') : confirmText}
          </Button>
        </Modal.Footer>
      </Modal>
      {displaySourcesModal && (
        <SourcesModal
          onClose={onClose}
          onConfirm={performRequest}
          confirmText={confirmText}
          preSelectAll
        />
      )}
    </>
  );
}

export default CollectionModal;
