/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge, typographyVariants} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {mutedTitle} from '#/operate/shared/mutedTitle';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {StateIcon} from '#/operate/shared/StateIcon/shadcn.components/StateIcon';

type Column = {
	title?: string;
	content: React.ReactNode;
	dataTestId?: string;
	hideOverflowingContent?: boolean;
	hidden?: boolean;
};

type InstanceHeaderProps = {
	state: React.ComponentProps<typeof StateIcon>['state'];
	instanceName: string;
	incidentsCount?: number;
	nameSubtitle?: React.ReactNode;
	headerColumns: string[];
	bodyColumns: Column[];
	additionalContent?: React.ReactNode;
	hideBottomBorder?: boolean;
};

const InstanceHeader: React.FC<InstanceHeaderProps> = ({
	state,
	headerColumns,
	bodyColumns,
	instanceName,
	incidentsCount = 0,
	nameSubtitle,
	additionalContent,
	hideBottomBorder = false,
}) => {
	const {t} = useTranslation();
	const hasSubtitleRow = incidentsCount > 0 || Boolean(nameSubtitle);

	return (
		<header
			data-testid="instance-header"
			role="group"
			aria-label={t('operate.shared.instanceHeader.label')}
			className={cn(
				'flex min-w-0 items-center gap-4 border-b border-border px-4 py-1',
				hideBottomBorder && 'border-b-0',
			)}
		>
			<StateIcon state={state} size={24} />

			<div className="mr-4 flex min-w-24 shrink flex-col gap-0.5">
				<TruncatedText text={instanceName} className={mutedTitle(hasSubtitleRow ? 'label-sm' : 'label-md')} />
				{hasSubtitleRow && (
					<div className="flex min-w-0 items-center gap-2">
						{incidentsCount > 0 && (
							<TruncatedText
								text={t('operate.shared.instanceHeader.incidentsCount', {count: incidentsCount})}
								className={cn('text-danger-foreground-subtle', typographyVariants({variant: 'label-md'}))}
							/>
						)}
						{nameSubtitle && <Badge variant="warning">{nameSubtitle}</Badge>}
					</div>
				)}
			</div>

			<table className="table-fixed border-separate border-spacing-y-0.5 text-neutral-foreground [&_td:not(:first-child)]:pl-8 [&_th:not(:first-child)]:pl-8">
				<thead>
					<tr>
						{headerColumns.map((column, index) => (
							<th key={index} className={mutedTitle('label-sm', 'text-left')}>
								{column}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					<tr>
						{bodyColumns.map((column, index) => {
							if (column.hidden) {
								return null;
							}
							const hideOverflowingContent = column.hideOverflowingContent ?? true;
							// Plain-text cells reveal their full text via TruncatedText; other content keeps the cell-level truncate.
							const isTruncatedText = hideOverflowingContent && typeof column.content === 'string';
							return (
								<td
									key={index}
									title={isTruncatedText && column.title === column.content ? undefined : column.title}
									data-testid={column.dataTestId}
									className={cn(
										typographyVariants({variant: 'label-md'}),
										hideOverflowingContent && !isTruncatedText && 'truncate',
									)}
								>
									{isTruncatedText ? <TruncatedText text={column.content as string} /> : column.content}
								</td>
							);
						})}
					</tr>
				</tbody>
			</table>
			<div className="ml-auto shrink-0">{additionalContent}</div>
		</header>
	);
};

export {InstanceHeader};
export type {Column};
