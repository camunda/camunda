/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, ComponentPropsWithoutRef} from 'react';
import {Button, Stack} from '@carbon/react';
import {Download} from '@carbon/icons-react';

import {Modal} from 'components';
import {get} from 'request';
import {showError} from 'notifications';
import {getExportCsvLimit} from 'config';
import {useErrorHandling, useDocs} from 'hooks';
import {User} from 'HOC';

import {t} from 'translation';

type LinkProps = {href: string; fileName?: string; retriever?: never};
type RetrieverProps = {
  retriever: () => Promise<Blob>;
  fileName: string;
  href?: never;
};

interface CommonProps extends ComponentPropsWithoutRef<typeof Button> {
  totalCount: number;
  // We take user as a prop instead of using the hook because user context is not accessible in HeatmapOverlay
  user: User | undefined;
}

export type DownloadButtonProps = CommonProps & (LinkProps | RetrieverProps);

export function DownloadButton({
  href,
  fileName,
  retriever,
  totalCount,
  user,
  ...props
}: DownloadButtonProps) {
  const [exportLimit, setExportLimit] = useState(1000);
  const [modalOpen, setModalOpen] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const {mightFail} = useErrorHandling();
  const {generateDocsLink} = useDocs();

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

  function startDownloadProcess() {
    setIsDownloading(true);
    return retriever ? retriever() : getData(href);
  }

  function triggerDownload() {
    mightFail(
      startDownloadProcess(),
      (data) => {
        const hiddenElement = document.createElement('a');
        hiddenElement.href = window.URL.createObjectURL(data);
        hiddenElement.download = getDownloadedFileName()!;
        hiddenElement.click();
        setModalOpen(false);
      },
      showError,
      () => setIsDownloading(false)
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
        renderIcon={Download}
        iconDescription={t('report.downloadCSV').toString()}
        {...props}
        onClick={() => (totalCount > exportLimit ? setModalOpen(true) : triggerDownload())}
        disabled={isDownloading}
      />
      <Modal open={modalOpen} onClose={closeModal} className="DownloadButtonConfirmationModal">
        <Modal.Header title={t('report.downloadCSV')} />
        <Modal.Content>
          <Stack gap={4}>
            <p>
              <b>{t('common.csvLimit.Warning')}</b>
            </p>
            <p>{t('common.csvLimit.info', {exportLimit, totalCount})}</p>
            <p>
              {t('common.csvLimit.exportApi', {
                docsLink: generateDocsLink('apis-clients/optimize-api/report/get-data-export/'),
              })}
            </p>
          </Stack>
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" onClick={closeModal}>
            {t('common.cancel')}
          </Button>
          <Button onClick={triggerDownload} disabled={isDownloading}>
            {t('common.download')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

async function getData(url: string) {
  const response = await get(url);
  return await response.blob();
}

export default DownloadButton;
