/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import ReactMarkdown from 'react-markdown';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {t, getLanguage} from 'translation';
import {showError} from 'notifications';
import {getOptimizeVersion} from 'config';

import {isChangeLogSeen, setChangeLogAsSeen, getMarkdownText} from './service';

import './WhatsNewModal.scss';

export function WhatsNewModal({open, onClose, mightFail}) {
  const [optimizeVersion, setOptimizeVersion] = useState(null);
  const [seen, setSeen] = useState(true);
  const [modalContent, setModalContent] = useState('');

  useEffect(() => {
    mightFail(isChangeLogSeen(), ({seen}) => setSeen(seen), showError);
    (async () => {
      setOptimizeVersion(await getOptimizeVersion());
    })();
  }, [mightFail]);

  useEffect(() => {
    if ((open || !seen) && !modalContent) {
      const localCode = getLanguage();
      mightFail(getMarkdownText(localCode), setModalContent, showError);
    }
  }, [mightFail, modalContent, open, seen]);

  const closeModal = () => {
    if (!seen) {
      setChangeLogAsSeen();
      setSeen(true);
    }
    onClose();
  };

  return (
    <Modal className="WhatsNewModal" open={open || !seen} onClose={closeModal}>
      <Modal.Header>
        {t('whatsNew.modalHeader')} {optimizeVersion}
      </Modal.Header>
      <Modal.Content>
        {modalContent ? <ReactMarkdown>{modalContent}</ReactMarkdown> : <LoadingIndicator />}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={closeModal}>
          {t('common.close')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(WhatsNewModal);
