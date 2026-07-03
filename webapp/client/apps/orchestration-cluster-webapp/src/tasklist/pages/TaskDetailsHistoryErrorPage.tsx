/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Layer, Link, Stack} from '@carbon/react';
import {Launch} from '@carbon/react/icons';
import type {ErrorComponentProps} from '@tanstack/react-router';
import {Trans, useTranslation} from 'react-i18next';
import {requestErrorSchema} from '#/shared/http/request';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import SvgForbidden from '#/shared/svg/Forbidden';
import styles from './TaskDetailsHistoryErrorPage.module.scss';

const HTTP_STATUS_FORBIDDEN = 403;
const DOCS_URL = 'https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/';

const TaskDetailsHistoryErrorPage: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const result = requestErrorSchema.safeParse(error);

	if (
		result.success &&
		result.data.variant === 'failed-response' &&
		result.data.response.status === HTTP_STATUS_FORBIDDEN
	) {
		return <ForbiddenHistoryPage />;
	}

	return <GenericHistoryErrorPage onRetry={reset} />;
};

const ForbiddenHistoryPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div className={styles.container} data-testid="task-details-history-forbidden">
			<Layer withBackground className={styles.card}>
				<Stack orientation="horizontal" gap={6}>
					<SvgForbidden aria-hidden />
					<Stack gap={6}>
						<Stack gap={3}>
							<h3 className={styles.title}>{t('tasklist.taskDetailsHistoryForbiddenTitle')}</h3>
							<div className={styles.description}>
								<Trans i18nKey="tasklist.taskDetailsHistoryForbiddenDesc" components={{strong: <strong />}} />
							</div>
						</Stack>
						<Link href={DOCS_URL} target="_blank" renderIcon={Launch}>
							{t('tasklist.taskDetailsHistoryForbiddenLinkLabel')}
						</Link>
					</Stack>
				</Stack>
			</Layer>
		</div>
	);
};

type GenericHistoryErrorPageProps = {
	onRetry: () => void;
};

const GenericHistoryErrorPage: React.FC<GenericHistoryErrorPageProps> = ({onRetry}) => {
	const {t} = useTranslation();

	return (
		<div className={styles.container} data-testid="task-details-history-error">
			<Layer withBackground className={styles.card}>
				<Stack orientation="horizontal" gap={6}>
					<SvgErrorRobot aria-hidden />
					<Stack gap={6}>
						<Stack gap={3}>
							<h3 className={styles.title}>{t('tasklist.taskDetailsHistoryErrorTitle')}</h3>
							<p className={styles.description}>{t('tasklist.taskDetailsHistoryErrorMessage')}</p>
						</Stack>
						<Button onClick={onRetry}>{t('errorGenericErrorPageButtonLabel')}</Button>
					</Stack>
				</Stack>
			</Layer>
		</div>
	);
};

export {TaskDetailsHistoryErrorPage};
