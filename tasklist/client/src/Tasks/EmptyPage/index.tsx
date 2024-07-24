/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Grid, Link} from '@carbon/react';
import CheckImage from 'modules/images/orange-check-mark.svg';
import {getStateLocally} from 'modules/utils/localStorage';
import {Restricted} from 'modules/components/Restricted';
import {useTasks} from 'modules/queries/useTasks';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {decodeTaskEmptyPageRef} from 'modules/utils/reftags';
import {useSearchParams} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import styles from './styles.module.scss';

const EmptyPage: React.FC = () => {
  const filters = useTaskFilters();
  const {isPending, data} = useTasks(filters);
  const tasks = data?.pages.flat() ?? [];
  const hasNoTasks = tasks.length === 0;
  const isOldUser = getStateLocally('hasCompletedTask') === true;
  const [searchParams, setSearchParams] = useSearchParams();

  const {t} = useTranslation();

  useEffect(() => {
    const ref = searchParams.get('ref');
    if (ref !== null) {
      searchParams.delete('ref');
      setSearchParams(searchParams, {replace: true});
    }

    const taskEmptyPageOpenedRef = decodeTaskEmptyPageRef(ref);
    tracking.track({
      eventName: 'task-empty-page-opened',
      ...(taskEmptyPageOpenedRef ?? {}),
    });
  }, [searchParams, setSearchParams]);

  if (isPending) {
    return <span data-testid="loading-state" />;
  }

  if (hasNoTasks && isOldUser) {
    return null;
  }

  return (
    <Grid className={styles.container} condensed>
      <Column
        className={styles.imageContainer}
        sm={1}
        md={{
          span: 2,
          offset: 1,
        }}
        lg={{
          span: 2,
          offset: 4,
        }}
        xlg={{
          span: 1,
          offset: 5,
        }}
      >
        <img className={styles.image} src={CheckImage} alt="" />
      </Column>
      <Column
        className={isOldUser ? styles.oldUserText : styles.newUserText}
        sm={3}
        md={5}
        lg={10}
        xlg={10}
      >
        {isOldUser ? (
          <Restricted
            fallback={<h3>{t('pickATaskToViewDetails')}</h3>}
            scopes={['write']}
          >
            <h3>{t('pickATaskToWorkOn')}</h3>
          </Restricted>
        ) : (
          <>
            <h3>{t('welcomeToTasklist')}</h3>
            <p data-testid="first-paragraph">
              {t('userTasksDescription')}
              <br />
              {t('bpmnDiagramAndForms')}
            </p>
            {!hasNoTasks && <p>{t('selectTaskToViewDetails')}</p>}
            <p data-testid="tutorial-paragraph">
              {t('followTutorialTo')}{' '}
              <Link
                href="https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks"
                target="_blank"
                rel="noreferrer"
                inline
              >
                {t('learnHowToCreateTasks')}
              </Link>
            </p>
          </>
        )}
      </Column>
    </Grid>
  );
};

EmptyPage.displayName = 'EmptyPage';

export {EmptyPage as Component};
