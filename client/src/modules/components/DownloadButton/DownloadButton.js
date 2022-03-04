/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';

import {Button, Modal} from 'components';
import {withErrorHandling, withDocs} from 'HOC';
import {get} from 'request';
import {showError} from 'notifications';
import {getExportCsvLimit} from 'config';

import {t} from 'translation';

export function DownloadButton({
  href,
  fileName,
  onClick,
  mightFail,
  error,
  resetError,
  retriever,
  totalCount,
  docsLink,
  ...props
}) {
  const [exportLimit, setExportLimit] = useState(1000);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    (async () => {
      const limit = await getExportCsvLimit();
      setExportLimit(limit);
    })();
  }, []);

  function triggerDownload(evt) {
    onClick?.(evt);
    mightFail(
      retriever || getData(href),
      (data) => {
        const hiddenElement = document.createElement('a');
        hiddenElement.href = window.URL.createObjectURL(data);
        hiddenElement.download = fileName || href.substring(href.lastIndexOf('/') + 1);
        hiddenElement.click();
      },
      showError
    );
  }

  const closeModal = () => {
    setModalOpen(false);
  };

  const displayModal = totalCount > exportLimit;

  return (
    <>
      <Button
        {...props}
        onClick={(evt) => (displayModal ? setModalOpen(true) : triggerDownload(evt))}
      />
      {displayModal && (
        <Modal open={modalOpen} onClose={closeModal}>
          <Modal.Header>Download CSV</Modal.Header>
          <Modal.Content>
            <p>
              <b>{t('common.csvLimit.Warning')}</b>
            </p>
            <p>{t('common.csvLimit.info', {exportLimit, totalCount})}</p>
            <p
              dangerouslySetInnerHTML={{
                __html: t('common.csvLimit.exportApi', {
                  docsLink: docsLink + 'technical-guide/rest-api/report/get-data-export',
                }),
              }}
            />
          </Modal.Content>
          <Modal.Actions>
            <Button main onClick={closeModal}>
              {t('common.cancel')}
            </Button>
            <Button main primary onClick={triggerDownload}>
              {t('common.download')}
            </Button>
          </Modal.Actions>
        </Modal>
      )}
    </>
  );
}

async function getData(url) {
  const response = await get(url);
  return await response.blob();
}

export default withErrorHandling(withDocs(DownloadButton));
