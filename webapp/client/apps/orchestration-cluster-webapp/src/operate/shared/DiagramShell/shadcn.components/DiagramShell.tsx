/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {LoaderCircle, Workflow} from '@camunda/design-system/icons';
import {EmptyState} from '@camunda/design-system';
import {DataLoadErrorAlert} from '../../DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';

type DefaultProps = {
	children?: React.ReactNode;
	status: 'error' | 'loading' | 'content' | 'forbidden';
	contentLabel?: string;
	onRetry?: () => void;
};

type WithEmptyMessageProps = {
	children?: React.ReactNode;
	status: 'error' | 'empty' | 'loading' | 'content';
	contentLabel?: string;
	onRetry?: () => void;
	emptyMessage: {heading: string; description: string};
	messagePosition?: 'top' | 'center';
};

const DiagramShell: React.FC<DefaultProps | WithEmptyMessageProps> = ({
	children,
	status,
	contentLabel,
	onRetry,
	...props
}) => {
	const {t} = useTranslation();
	const position = 'messagePosition' in props ? props.messagePosition : 'top';
	const messageClassName = position === 'center' ? 'self-center' : 'mt-8';

	return (
		<div
			data-testid="diagram-body"
			{...(status === 'content' && {
				tabIndex: 0,
				role: 'region',
				'aria-label': contentLabel ?? t('operate.shared.diagramShell.content'),
			})}
			className="relative flex h-full min-h-0 grow items-start justify-center overflow-y-auto"
		>
			{status === 'content' && children}

			{status === 'loading' && (
				<>
					<div
						role="status"
						aria-label={t('operate.shared.diagramShell.loading')}
						data-testid="diagram-spinner"
						className="absolute inset-0 z-10 flex items-center justify-center bg-background/70"
					>
						<LoaderCircle className="size-8 animate-spin" aria-hidden />
					</div>
					{children}
				</>
			)}

			{status === 'empty' && 'emptyMessage' in props && (
				<EmptyState
					size="sm"
					headingLevel={3}
					icon={<Workflow aria-hidden />}
					className={messageClassName}
					heading={props.emptyMessage.heading}
					description={props.emptyMessage.description}
				/>
			)}

			{status === 'forbidden' && (
				<DataLoadErrorAlert
					className={messageClassName}
					title={t('operate.decisionInstance.ds.forbidden.heading')}
					description={t('operate.decisionInstance.forbidden.description')}
				/>
			)}

			{status === 'error' && <DataLoadErrorAlert className={messageClassName} onRetry={onRetry} />}
		</div>
	);
};

export {DiagramShell};
