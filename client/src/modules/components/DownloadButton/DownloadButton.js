/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {Button, Modal} from 'components';
import {withErrorHandling, withDocs, withUser} from 'HOC';
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
  user,
  getUser,
  refreshUser,
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
      retriever ? retriever() : getData(href),
      (data) => {
        const hiddenElement = document.createElement('a');
        hiddenElement.href = window.URL.createObjectURL(data);
        hiddenElement.download = fileName || href.substring(href.lastIndexOf('/') + 1);
        hiddenElement.click();
        setModalOpen(false);
      },
      showError
    );
  }

  const closeModal = () => {
    setModalOpen(false);
  };

  if (!user?.authorizations.includes('csv_export')) {
    return null;
  }

  return (
    <>
      <Button
        {...props}
        onClick={(evt) => (totalCount > exportLimit ? setModalOpen(true) : triggerDownload(evt))}
      />
      {modalOpen && (
        <Modal open onClose={closeModal}>
          <Modal.Header>{t('report.downloadCSV')}</Modal.Header>
          <Modal.Content>
            <p>
              <b>{t('common.csvLimit.Warning')}</b>
            </p>
            <p>{t('common.csvLimit.info', {exportLimit, totalCount})}</p>
            <p>
              {t('common.csvLimit.exportApi', {
                docsLink: docsLink + 'apis-clients/optimize-api/report/get-data-export/',
              })}
            </p>
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

export default withErrorHandling(withDocs(withUser(DownloadButton)));
