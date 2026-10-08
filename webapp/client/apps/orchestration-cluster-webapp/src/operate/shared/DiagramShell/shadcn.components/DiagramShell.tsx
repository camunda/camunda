/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {LoaderCircle} from '@camunda/design-system/icons';
import {EmptyMessage} from '../../EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '../../ErrorMessage/shadcn.components/ErrorMessage';

type DefaultProps = {
	children: React.ReactNode;
	status: 'error' | 'loading' | 'content' | 'forbidden';
};

type WithEmptyMessageProps = {
	children: React.ReactNode;
	status: 'error' | 'empty' | 'loading' | 'content';
	emptyMessage: React.ComponentProps<typeof EmptyMessage>;
	messagePosition?: 'top' | 'center';
};

const DiagramShell: React.FC<DefaultProps | WithEmptyMessageProps> = ({children, status, ...props}) => {
	const {t} = useTranslation();
	const position = 'messagePosition' in props ? props.messagePosition : 'top';
	const messageClassName = position === 'center' ? 'self-center' : 'mt-8';

	return (
		<div
			data-testid="diagram-body"
			tabIndex={0}
			className="relative flex h-full min-h-[200px] grow items-start justify-center overflow-y-auto"
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
				<EmptyMessage className={messageClassName} {...props.emptyMessage} />
			)}

			{status === 'forbidden' && (
				<ErrorMessage
					className={messageClassName}
					message={t('operate.shared.diagramShell.forbiddenMessage')}
					additionalInfo={t('operate.shared.diagramShell.forbiddenAdditionalInfo')}
				/>
			)}

			{status === 'error' && <ErrorMessage className={messageClassName} />}
		</div>
	);
};

export {DiagramShell};
