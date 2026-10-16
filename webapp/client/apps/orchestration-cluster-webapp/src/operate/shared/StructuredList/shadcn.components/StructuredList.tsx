/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {Table, TableCell, TableHead, TableHeader, TableRow} from '@camunda/design-system';
import {InfiniteScroller} from '#/operate/shared/InfiniteScroller/InfiniteScroller';
import {cn} from '#/shared/cn';

type Column = {
	cellContent: React.ReactNode;
	width?: string;
};

type RowProps = {
	columns: Column[];
	dataTestId?: string;
	key: string;
};

type Props = {
	label: string;
	headerSize?: 'sm' | 'md';
	headerColumns: Column[];
	rows: RowProps[];
	className?: string;
	/**
	 * Extra row(s) rendered directly inside the `<tbody>`, ahead of `rows`. Typed as `<tr>`
	 * element(s) rather than `React.ReactNode` (unlike the Carbon counterpart, which wraps its
	 * body in a non-table `StructuredListBody` and tolerates arbitrary content): this component
	 * renders a real `<table>`, where only `<tr>` is valid markup directly under `<tbody>`.
	 */
	dynamicRows?: React.ReactElement<React.ComponentProps<'tr'>> | React.ReactElement<React.ComponentProps<'tr'>>[];
	verticalCellPadding?: string;
	dataTestId?: string;
	isFlush?: boolean;
} & Pick<React.ComponentProps<typeof InfiniteScroller>, 'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'>;

const StructuredRows: React.FC<Pick<Props, 'rows' | 'verticalCellPadding'>> = ({rows, verticalCellPadding}) => {
	return (
		<>
			{rows.map(({key, dataTestId, columns}) => (
				<TableRow key={key} data-testid={dataTestId}>
					{columns.map(({cellContent, width}, index) => (
						<TableCell
							key={index}
							className="align-top"
							style={{
								width,
								...(verticalCellPadding ? {paddingTop: verticalCellPadding, paddingBottom: verticalCellPadding} : {}),
							}}
							onFocus={(event) => {
								event.stopPropagation();
							}}
						>
							{cellContent}
						</TableCell>
					))}
				</TableRow>
			))}
		</>
	);
};

const StructuredList: React.FC<Props> = ({
	label,
	headerSize = 'md',
	headerColumns,
	rows,
	className,
	dynamicRows,
	verticalCellPadding,
	dataTestId,
	onVerticalScrollStartReach,
	onVerticalScrollEndReach,
	isFlush = true,
}) => {
	const scrollableContentRef = useRef<HTMLDivElement | null>(null);

	return (
		<div className={cn('overflow-y-auto', className)} data-testid={dataTestId} ref={scrollableContentRef}>
			<Table aria-label={label}>
				<TableHeader>
					<TableRow>
						{headerColumns.map(({cellContent, width}, index) => (
							<TableHead
								key={index}
								className={cn(headerSize === 'sm' && 'text-xs', isFlush && index === 0 && 'pl-0!')}
								style={{width}}
							>
								{cellContent}
							</TableHead>
						))}
					</TableRow>
				</TableHeader>
				<InfiniteScroller
					onVerticalScrollStartReach={onVerticalScrollStartReach}
					onVerticalScrollEndReach={onVerticalScrollEndReach}
					scrollableContainerRef={scrollableContentRef}
				>
					<tbody
						className={cn(
							'[&_tr:last-child]:border-0',
							// Applied at the tbody level (rather than per-cell in `StructuredRows`) so
							// it also reaches `dynamicRows`, which renders its own `<tr>`/`<td>` markup
							// directly as a tbody child and would otherwise keep the design system's
							// default leading-cell padding. The `!` important modifier is required:
							// this descendant selector and `TableCell`'s own `px-4`/`px-5` size class
							// land in the same Tailwind layer, so without it the cascade would fall
							// back to source order (which utility class the compiler happened to emit
							// first) rather than this intentional override.
							isFlush && '[&_tr>td:first-child]:pl-0!',
						)}
					>
						{dynamicRows}
						<StructuredRows rows={rows} verticalCellPadding={verticalCellPadding} />
					</tbody>
				</InfiniteScroller>
			</Table>
		</div>
	);
};

export {StructuredList, StructuredRows};
