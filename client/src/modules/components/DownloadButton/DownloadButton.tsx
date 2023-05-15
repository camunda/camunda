/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, ComponentPropsWithoutRef} from 'react';
import {Button} from '@carbon/react';

import {Button as LegacyButton, CarbonModal as Modal} from 'components';
import {
  withErrorHandling,
  withDocs,
  withUser,
  WithUserProps,
  WithErrorHandlingProps,
  WithDocsProps,
} from 'HOC';
import {get} from 'request';
import {showError} from 'notifications';
import {getExportCsvLimit} from 'config';

import {t} from 'translation';

type LinkProps = {href: string; fileName?: string; retriever?: never};
type RetrieverProps = {
  retriever: () => Promise<Blob>;
  fileName: string;
  href?: never;
};

interface CommonProps
  extends WithUserProps,
    WithErrorHandlingProps,
    WithDocsProps,
    ComponentPropsWithoutRef<'button'> {
  totalCount: number;
}

export type DownloadButtonProps = CommonProps & (LinkProps | RetrieverProps);

export function DownloadButton({
  href,
  fileName,
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
}: DownloadButtonProps) {
  const [exportLimit, setExportLimit] = useState(1000);
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    (async () => {
      const limit = await getExportCsvLimit();
      setExportLimit(limit);
    })();
  }, []);

  function getDownloadedFileName() {
    if (retriever) {
      return fileName;
    }

    if (href) {
      return fileName || href.substring(href.lastIndexOf('/') + 1);
    }
  }

  function triggerDownload() {
    mightFail(
      retriever ? retriever() : getData(href),
      (data) => {
        const hiddenElement = document.createElement('a');
        hiddenElement.href = window.URL.createObjectURL(data);
        hiddenElement.download = getDownloadedFileName()!;
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
      <LegacyButton
        {...props}
        onClick={() => (totalCount > exportLimit ? setModalOpen(true) : triggerDownload())}
      />
      <Modal open={modalOpen} onClose={closeModal}>
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
        <Modal.Footer>
          <Button kind="secondary" onClick={closeModal}>
            {t('common.cancel')}
          </Button>
          <Button onClick={triggerDownload}>{t('common.download')}</Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

async function getData(url: string) {
  const response = await get(url);
  return await response.blob();
}

export default withErrorHandling(withDocs(withUser(DownloadButton)));
