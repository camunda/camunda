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
	/** Rendered before the static `rows` inside the table body — must produce valid `<tr>` markup. */
	dynamicRows?: React.ReactNode;
	verticalCellPadding?: string;
	dataTestId?: string;
	isFlush?: boolean;
} & Pick<React.ComponentProps<typeof InfiniteScroller>, 'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'>;

const StructuredRows: React.FC<Pick<Props, 'rows' | 'verticalCellPadding' | 'isFlush'>> = ({
	rows,
	verticalCellPadding,
	isFlush,
}) => {
	return (
		<>
			{rows.map(({key, dataTestId, columns}) => (
				<TableRow key={key} data-testid={dataTestId}>
					{columns.map(({cellContent, width}, index) => (
						<TableCell
							key={index}
							// `verticalCellPadding`/`width` are consumer-supplied runtime values, not static
							// design tokens, so they're applied inline rather than as Tailwind utilities.
							className={cn('align-top', isFlush && index === 0 && 'pl-0')}
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
					<TableRow tabIndex={0}>
						{headerColumns.map(({cellContent, width}, index) => (
							<TableHead
								key={index}
								className={cn(headerSize === 'sm' && 'text-xs', isFlush && index === 0 && 'pl-0')}
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
					{/*
					 * `InfiniteScroller` clones its child to attach a DOM ref, which only works on
					 * elements/components that forward refs. The design system's `TableBody` is a plain
					 * function component (no `forwardRef`), so the body is rendered as a raw `<tbody>`
					 * here, mirroring `TableBody`'s own styling, to stay compatible with that contract.
					 */}
					<tbody className="[&_tr:last-child]:border-0" role="rowgroup">
						{dynamicRows}
						<StructuredRows rows={rows} verticalCellPadding={verticalCellPadding} isFlush={isFlush} />
					</tbody>
				</InfiniteScroller>
			</Table>
		</div>
	);
};

export {StructuredList, StructuredRows};
