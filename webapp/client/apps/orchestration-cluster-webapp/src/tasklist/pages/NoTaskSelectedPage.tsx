/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Column, Grid, Link} from '@carbon/react';
import {useTranslation, Trans} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import styles from './NoTaskSelectedPage.module.scss';
import {SvgOrangeCheckMark} from '#/shared/svg/OrangeCheckMark';

type Props = {
	hasNoTasks: boolean;
};

const NoTaskSelectedPage: React.FC<Props> = ({hasNoTasks}) => {
	const isOldUser = getStateLocally('hasCompletedTask') === true;
	const {t} = useTranslation();

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
				<SvgOrangeCheckMark className={styles.image} aria-hidden />
			</Column>
			<Column className={isOldUser ? styles.oldUserText : styles.newUserText} sm={3} md={5} lg={10} xlg={10}>
				{isOldUser ? (
					<h3>{t('taskEmptyPickPrompt')}</h3>
				) : (
					<>
						<h3>{t('taskEmptyHeader')}</h3>
						<p data-testid="first-paragraph">
							{t('taskEmptyDetail1')}
							<br />
							{t('taskEmptyDetail2')}
						</p>
						{!hasNoTasks && <p>{t('taskEmptyTaskAvailablePrompt')}</p>}
						<p data-testid="tutorial-paragraph">
							<Trans i18nKey="taskEmptyTutorial">
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
