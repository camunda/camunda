/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Grid, Link} from '#/shared/design-system-compat';
import {useTranslation, Trans} from 'react-i18next';
import {Check} from 'lucide-react';
import {Button, EmptyState} from '@camunda/design-system';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
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

	// DS-only (see below — only ever rendered when featureFlags.dsTasklistUI is
	// on and this is the first-time "Welcome to Tasklist" state, not the
	// isOldUser "pick a task" prompt, which keeps its existing Grid/Column
	// markup unchanged for both UIs). Uses the DS's own EmptyState component
	// instead of the hand-rolled Grid/Column/h3/p structure below.
	if (featureFlags.dsTasklistUI && !isOldUser) {
		return (
			<div className={styles.containerDS}>
				<EmptyState
					icon={<Check aria-hidden />}
					heading={t('tasklist.taskEmptyHeader')}
					description={
						<>
							{t('tasklist.taskEmptyDetail1')} {t('tasklist.taskEmptyDetail2')}
							{!hasNoTasks ? <> {t('tasklist.taskEmptyTaskAvailablePrompt')}</> : null}
						</>
					}
					action={
						<Button asChild>
							<a href={TUTORIAL_URL} target="_blank" rel="noreferrer">
								{t('tasklist.taskEmptyTutorialCta')}
							</a>
						</Button>
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
				className={cn(
					isOldUser ? styles.oldUserText : styles.newUserText,
					// Only isOldUser reaches this Column with the flag on — the
					// !isOldUser DS path returns its own EmptyState render above.
					isOldUser && featureFlags.dsTasklistUI && styles.oldUserTextDS,
				)}
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
