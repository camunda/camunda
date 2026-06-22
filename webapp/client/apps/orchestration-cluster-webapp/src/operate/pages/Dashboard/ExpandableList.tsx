/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTableSkeleton, InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {ReactElement, ReactNode, UIEvent} from 'react';
import SvgErrorRobot from '#/shared/svg/ErrorRobot';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import {PartiallyExpandableDataTable} from './PartiallyExpandableDataTable/PartiallyExpandableDataTable';
import {ScrollableList, LoadingRow} from './styled';

type Row = {id: string; [key: string]: ReactNode};

type Props = {
	isPending: boolean;
	isError: boolean;
	emptyState?: ReactNode;
	listTestId: string;
	dataTestId: string;
	header: string;
	rows: Row[];
	expandedContents: Record<string, ReactElement<{tabIndex: number}>>;
	isFetchingNextPage: boolean;
	isFetchingPreviousPage: boolean;
	onScroll: (event: UIEvent<HTMLDivElement>) => void;
};

const ExpandableList: React.FC<Props> = ({
	isPending,
	isError,
	emptyState,
	listTestId,
	dataTestId,
	header,
	rows,
	expandedContents,
	isFetchingNextPage,
	isFetchingPreviousPage,
	onScroll,
}) => {
	const {t} = useTranslation();

	if (isPending) {
		return <DataTableSkeleton columnCount={1} rowCount={20} showHeader={false} showToolbar={false} />;
	}

	if (isError) {
		return (
			<EmptyState
				icon={<SvgErrorRobot aria-hidden />}
				heading={t('operate.dashboard.fetchErrorHeading')}
				description={t('operate.dashboard.fetchErrorDescription')}
			/>
		);
	}

	if (emptyState !== undefined) {
		return <>{emptyState}</>;
	}

	return (
		<ScrollableList onScroll={onScroll} data-testid={listTestId}>
			{isFetchingPreviousPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
			<PartiallyExpandableDataTable
				dataTestId={dataTestId}
				headers={[{key: header, header}]}
				rows={rows}
				expandedContents={expandedContents}
			/>
			{isFetchingNextPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
		</ScrollableList>
	);
};

export {ExpandableList};
