/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@camunda/design-system';
import {ExternalLink} from 'lucide-react';
import {useNavigate, type ErrorComponentProps} from '@tanstack/react-router';
import {Trans, useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import SvgForbidden from '#/shared/svg/Forbidden';
import {useCallback} from 'react';

const DOCS_URL = 'https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/';

const TaskDetailsHistoryErrorPage: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const navigate = useNavigate();

	const handleRetry = useCallback(() => {
		reset();
		navigate({to: '.', replace: true});
	}, [reset, navigate]);

	if (error instanceof ForbiddenError) {
		return <ForbiddenHistoryPage />;
	}

	return <GenericHistoryErrorPage onRetry={handleRetry} />;
};

const ForbiddenHistoryPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<div
			className="grid min-h-0 w-full flex-1 content-center p-8 max-xl:p-4"
			data-testid="task-details-history-forbidden"
		>
			<div className="flex min-w-0 gap-6 rounded-xl border border-border bg-background p-8 shadow-sm max-xl:p-4">
				<SvgForbidden className="shrink-0 max-xl:hidden" aria-hidden />
				<div className="flex min-w-0 flex-col items-start gap-6">
					<div className="flex flex-col gap-2">
						<h2 className="text-xl leading-7 font-normal">{t('tasklist.taskDetailsHistoryForbiddenTitle')}</h2>
						<div className="text-sm leading-5 font-normal">
							<Trans i18nKey="tasklist.taskDetailsHistoryForbiddenDesc" components={{strong: <strong />}} />
						</div>
					</div>
					<Button asChild variant="link" className="h-auto max-w-full justify-start whitespace-normal p-0 text-left">
						<a href={DOCS_URL} target="_blank" rel="noreferrer">
							{t('tasklist.taskDetailsHistoryForbiddenLinkLabel')}
							<ExternalLink aria-hidden />
						</a>
					</Button>
				</div>
			</div>
		</div>
	);
};

type GenericHistoryErrorPageProps = {
	onRetry: () => void;
};

const GenericHistoryErrorPage: React.FC<GenericHistoryErrorPageProps> = ({onRetry}) => {
	const {t} = useTranslation();

	return (
		<div className="grid min-h-0 w-full flex-1 content-center p-8 max-xl:p-4" data-testid="task-details-history-error">
			<div className="flex min-w-0 gap-6 rounded-xl border border-border bg-background p-8 shadow-sm max-xl:p-4">
				<SvgErrorRobot className="shrink-0 max-xl:hidden" aria-hidden />
				<div className="flex min-w-0 flex-col items-start gap-6">
					<div className="flex flex-col gap-2">
						<h2 className="text-xl leading-7 font-normal">{t('tasklist.taskDetailsHistoryErrorTitle')}</h2>
						<p className="text-sm leading-5 font-normal">{t('tasklist.taskDetailsHistoryErrorMessage')}</p>
					</div>
					<Button onClick={onRetry}>{t('errorGenericErrorPageButtonLabel')}</Button>
				</div>
			</div>
		</div>
	);
};

export {TaskDetailsHistoryErrorPage};
