/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Grid, Link} from '#/shared/design-system-compat';
import {useTranslation, Trans} from 'react-i18next';
import {Check, ListTodo} from 'lucide-react';
import {Button, EmptyState} from '@camunda/design-system';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {featureFlags} from '#/shared/feature-flags';
import styles from './NoTaskSelectedPage.module.scss';
import {SvgOrangeCheckMark} from '#/shared/svg/OrangeCheckMark';

const TUTORIAL_URL = 'https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks';

type Props = {
	hasNoTasks: boolean;
};

const NoTaskSelectedPage: React.FC<Props> = ({hasNoTasks}) => {
	const isOldUser = getStateLocally('tasklist.hasCompletedTask') === true;
	const {t} = useTranslation();

	if (hasNoTasks && isOldUser) {
		return null;
	}

	// DS-only. Both the first-time "Welcome to Tasklist" state and the
	// returning-user "pick a task" prompt render through the DS's own
	// EmptyState component instead of the hand-rolled Grid/Column/h3/p
	// markup below (Carbon-only now, for both isOldUser and !isOldUser).
	if (featureFlags.dsTasklistUI) {
		return (
			<div className={styles.containerDS}>
				<EmptyState
					icon={isOldUser ? <ListTodo aria-hidden /> : <Check aria-hidden />}
					heading={t(isOldUser ? 'tasklist.taskEmptyPickPrompt' : 'tasklist.taskEmptyHeader')}
					description={
						isOldUser ? undefined : (
							<>
								{t('tasklist.taskEmptyDetail1')} {t('tasklist.taskEmptyDetail2')}
								{!hasNoTasks ? <> {t('tasklist.taskEmptyTaskAvailablePrompt')}</> : null}
							</>
						)
					}
					action={
						isOldUser ? undefined : (
							<Button asChild>
								<a href={TUTORIAL_URL} target="_blank" rel="noreferrer">
									{t('tasklist.taskEmptyTutorialCta')}
								</a>
							</Button>
						)
					}
				/>
			</div>
		);
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
				<SvgOrangeCheckMark className={styles.image} aria-hidden />
			</Column>
			<Column
				className={isOldUser ? styles.oldUserText : styles.newUserText}
				sm={3}
				md={5}
				lg={10}
				xlg={10}
			>
				{isOldUser ? (
					<h3>{t('tasklist.taskEmptyPickPrompt')}</h3>
				) : (
					<>
						<h3>{t('tasklist.taskEmptyHeader')}</h3>
						<p data-testid="first-paragraph">
							{t('tasklist.taskEmptyDetail1')}
							<br />
							{t('tasklist.taskEmptyDetail2')}
						</p>
						{!hasNoTasks && <p>{t('tasklist.taskEmptyTaskAvailablePrompt')}</p>}
						<p data-testid="tutorial-paragraph">
							<Trans i18nKey="tasklist.taskEmptyTutorial">
								Follow our tutorial to{' '}
								<Link
									href="https://modeler.cloud.camunda.io/tutorial/quick-start-human-tasks"
									target="_blank"
									rel="noreferrer"
									inline
								>
									learn how to create tasks.
								</Link>
							</Trans>
						</p>
					</>
				)}
			</Column>
		</Grid>
	);
};

export {NoTaskSelectedPage};
