/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Heading, Text} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import type {McpToolProperties} from './mcpProcessTools';

type DescriptionProps = {
	content: string | null;
	isLast?: boolean;
};

const Description: React.FC<DescriptionProps> = ({content, isLast = false}) => {
	const {t} = useTranslation();

	return (
		<Text
			as="p"
			className={cn('max-w-[80ch] whitespace-pre-wrap wrap-break-word', {
				'mb-6': !isLast,
				'text-neutral-foreground-subtle italic': content === null,
			})}
		>
			{content ?? t('admin.mcpProcesses.informationMissing')}
		</Text>
	);
};

type ExpandedToolDetailsProps = {
	toolProperties: McpToolProperties;
};

const ExpandedToolDetails: React.FC<ExpandedToolDetailsProps> = ({toolProperties}) => {
	const {t} = useTranslation();

	return (
		<>
			<Heading as="h2" variant="heading-xs">
				{t('admin.mcpProcesses.toolPurpose')}
			</Heading>
			<Description content={toolProperties.purpose} />

			<Heading as="h2" variant="heading-xs">
				{t('admin.mcpProcesses.toolResults')}
			</Heading>
			<Description content={toolProperties.results} />

			<Heading as="h2" variant="heading-xs">
				{t('admin.mcpProcesses.toolWhenToUse')}
			</Heading>
			<Description content={toolProperties.whenToUse} />

			<Heading as="h2" variant="heading-xs">
				{t('admin.mcpProcesses.toolWhenNotToUse')}
			</Heading>
			<Description content={toolProperties.whenNotToUse} isLast />
		</>
	);
};

export {ExpandedToolDetails};
