/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstances} from 'modules/queries/useProcessInstances';
import capitalize from 'lodash/capitalize';
import {formatDate} from 'modules/utils/formatDate';
import {Skeleton} from './Skeleton';
import {match} from 'ts-pattern';
import {Stack, Layer} from '@carbon/react';
import styles from './styles.module.scss';
import {ProcessInstanceStateIcon} from './ProcessInstanceStateIcon';

const History: React.FC = () => {
  const {data: processInstances, status} = useProcessInstances();

  return (
    <div className={styles.container}>
      <span className={styles.header}>History</span>
      <div className={styles.itemContainer}>
        {match({status})
          .with({status: 'pending'}, () => <Skeleton />)
          .with({status: 'error'}, () => (
            <Layer>
              <Stack gap={3} className={styles.message}>
                <span className={styles.messageHeading}>
                  Oops! Something went wrong while fetching the history
                </span>
                <span className={styles.messageBody}>
                  Please check your internet connection and try again.
                </span>
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
                    <span className={styles.messageHeading}>
                      No history entries found
                    </span>
                    <span className={styles.messageBody}>
                      There is no history to display. Start a new process to see
                      it here.
                    </span>
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
