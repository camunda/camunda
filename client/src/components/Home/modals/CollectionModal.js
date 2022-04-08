/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Redirect} from 'react-router-dom';

import {Button, LabeledInput, Modal, Form} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {addSources} from '../service';
import SourcesModal from './SourcesModal';

import './CollectionModal.scss';

export function CollectionModal({
  onClose,
  title,
  confirmText,
  initialName,
  mightFail,
  onConfirm,
  showSourcesModal,
}) {
  const [name, setName] = useState(initialName);
  const [loading, setLoading] = useState(false);
  const [redirect, setRedirect] = useState(null);
  const [displaySourcesModal, setDisplaySourcesModal] = useState(false);

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

  const performRequest = (sources) => {
    mightFail(
      onConfirm(name),
      (id) => {
        if (id && sources) {
          mightFail(
            addSources(id, sources),
            () => {
              setLoading(false);
              setRedirect(id);
            },
            showError
          );
        }
      },
      (error) => {
        showError(error);
        setLoading(false);
      }
    );

    setLoading(true);
  };

  if (redirect) {
    return <Redirect to={`/collection/${redirect}/`} />;
  }

  return (
    <>
      <Modal
        className="CollectionModal"
        open={!displaySourcesModal}
        onClose={onClose}
        onConfirm={confirm}
      >
        <Modal.Header>{title}</Modal.Header>
        <Modal.Content>
          <Form>
            {showSourcesModal && <div className="info">{t('common.collection.modal.info')}</div>}
            <Form.Group>
              <LabeledInput
                type="text"
                label={t('common.collection.modal.inputLabel')}
                style={{width: '100%'}}
                value={name}
                onChange={({target: {value}}) => setName(value)}
                disabled={loading}
                autoComplete="off"
              />
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button main className="cancel" onClick={onClose} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button main primary className="confirm" disabled={!name || loading} onClick={confirm}>
            {showSourcesModal ? t('common.collection.modal.addDataSources') : confirmText}
          </Button>
        </Modal.Actions>
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

export default withErrorHandling(CollectionModal);
