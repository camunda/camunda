/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstances} from 'modules/queries/useProcessInstances';
import {useTranslation} from 'react-i18next';
import capitalize from 'lodash/capitalize';
import {formatDate} from 'modules/utils/formatDate';
import {Skeleton} from './Skeleton';
import {match} from 'ts-pattern';
import {Stack, Layer} from '@carbon/react';
import styles from './styles.module.scss';
import {ProcessInstanceStateIcon} from './ProcessInstanceStateIcon';

const History: React.FC = () => {
  const {data: processInstances, status} = useProcessInstances();
  const {t} = useTranslation();

  return (
    <div className={styles.container}>
      <span className={styles.header}>{t('historyTitle')}</span>
      <div className={styles.itemContainer}>
        {match({status})
          .with({status: 'pending'}, () => <Skeleton />)
          .with({status: 'error'}, () => (
            <Layer>
              <Stack gap={3} className={styles.message}>
                <span className={styles.messageHeading}>{t('errorFetchingHistoryTitle')}</span>
                <span className={styles.messageBody}>{t('errorFetchingHistoryBody')}</span>
              </Stack>
            </Layer>
          ))
          .with(
            {
              status: 'success',
            },
            () =>
              processInstances === undefined ||
              processInstances.length === 0 ? (
                <Layer>
                  <Stack gap={3} className={styles.message}>
                    <span className={styles.messageHeading}>{t('noHistoryEntriesFound')}</span>
                    <span className={styles.messageBody}>{t('noHistoryToDisplay')}</span>
                  </Stack>
                </Layer>
              ) : (
                processInstances.map(({id, process, creationDate, state}) => (
                  <Stack key={id} gap={3} className={styles.item}>
                    <span className={styles.itemName}>
                      {process.name ?? process.bpmnProcessId}
                    </span>
                    <span className={styles.itemId}>{id}</span>
                    <span className={styles.itemDetails}>
                      {formatDate(creationDate)} - {capitalize(state)}
                      <ProcessInstanceStateIcon
                        className={styles.icon}
                        state={state}
                        size={16}
                      />
                    </span>
                  </Stack>
                ))
              ),
          )
          .exhaustive()}
      </div>
    </div>
  );
};

export {History};
