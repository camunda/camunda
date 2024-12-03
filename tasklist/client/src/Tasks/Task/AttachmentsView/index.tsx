/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, ContainedList, ContainedListItem, Layer} from '@carbon/react';
import {Download, View} from '@carbon/react/icons';
import styles from './styles.module.scss';
import {useTaskDetailsParams} from 'modules/routing';
import {useTaskAttachments} from 'modules/queries/useTaskAttachments';
import {Skeleton} from './Skeleton';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {useTranslation} from 'react-i18next';
import ErrorRobotImage from 'modules/images/error-robot.svg';

const AttachmentsView: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {data, status} = useTaskAttachments(id);
  const {t} = useTranslation();

  if (status === 'pending') {
    return <Skeleton />;
  }

  if (status === 'error') {
    return (
      <Layer className={styles.errorContainer}>
        <C3EmptyState
          icon={{
            altText: t('startProcessFromFormErrorRobot'),
            path: ErrorRobotImage,
          }}
          heading={t('taskDetailsAttachmentsErrorMessageTitle')}
          description={t('taskDetailsAttachmentsErrorMessageBody')}
        />
      </Layer>
    );
  }

  return (
    <ContainedList
      label={t('taskDetailsAttachmentsTitle', {
        count: data.length,
      })}
      kind="on-page"
      isInset
      className={styles.containedList}
    >
      {data.map(({id, fileName}) => (
        <ContainedListItem
          key={id}
          aria-label={fileName}
          action={
            <>
              <Button
                type="button"
                kind="ghost"
                label={t('taskDetailsAttachmentsPreviewButtonLabel')}
                iconDescription={t('taskDetailsAttachmentsPreviewButtonLabel')}
                tooltipPosition="left"
                hasIconOnly
                renderIcon={View}
              />
              <Button
                type="button"
                kind="ghost"
                label={t('taskDetailsAttachmentsDownloadButtonLabel')}
                iconDescription={t('taskDetailsAttachmentsDownloadButtonLabel')}
                tooltipPosition="left"
                hasIconOnly
                renderIcon={Download}
              />
            </>
          }
        >
          {fileName}
        </ContainedListItem>
      ))}
    </ContainedList>
  );
};

export {AttachmentsView as Component};
