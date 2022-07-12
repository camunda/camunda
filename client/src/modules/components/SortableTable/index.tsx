/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';
import {observer} from 'mobx-react';
import Table from 'modules/components/Table';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';

import {ColumnHeader} from './ColumnHeader';
import {Skeleton} from './Skeleton';
import {Message} from './Message';
import {Row, SelectionType} from './Row';
import {Checkbox} from './Checkbox';

import {
  List,
  ScrollableContent,
  TH,
  THead,
  TRHeader,
  Spinner,
  SkeletonCheckboxBlock,
} from './styled';

type HeaderColumn = {
  content: string | React.ReactNode;
  sortKey?: string;
  isDisabled?: boolean;
  isDefault?: boolean;
  showExtraPadding?: boolean;
  paddingWidth?: number;
};

type RowProps = Omit<React.ComponentProps<typeof Row>, 'isSelected'> & {
  checkIsSelected?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
};

type Props = {
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  headerColumns: HeaderColumn[];
  rows: RowProps[];
  skeletonColumns?: React.ComponentProps<typeof Skeleton>['columns'];
  emptyMessage?: string;
  isScrollable?: boolean;
  selectionType?: SelectionType;
  checkIsAllSelected?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  onSelectAll?: () => void;
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
} & Pick<
  React.ComponentProps<typeof InfiniteScroller>,
  'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
>;

const SortableTable: React.FC<Props> = observer(
  ({
    state,
    headerColumns,
    rows,
    skeletonColumns,
    selectionType = 'none',
    isScrollable = true,
    emptyMessage,
    checkIsAllSelected,
    onSelectAll,
    onVerticalScrollStartReach,
    onVerticalScrollEndReach,
    onSort,
  }) => {
    let scrollableContentRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      if (state === 'loading') {
        scrollableContentRef?.current?.scrollTo?.(0, 0);
      }
    }, [state]);

    return (
      <List $isScrollable={isScrollable}>
        <ScrollableContent
          overflow={
            isScrollable
              ? state === 'skeleton'
                ? 'hidden'
                : 'auto'
              : 'initial'
          }
          ref={scrollableContentRef}
        >
          {state === 'loading' && <Spinner data-testid="instances-loader" />}
          <Table data-testid="data-table">
            <THead $isSticky={isScrollable}>
              <TRHeader>
                {headerColumns.map((header, index) => {
                  const {
                    content,
                    sortKey,
                    isDisabled,
                    isDefault,
                    showExtraPadding,
                    paddingWidth,
                  } = header;

                  return (
                    <TH key={index}>
                      <>
                        {index === 0 &&
                          selectionType === 'checkbox' &&
                          (state === 'skeleton' ? (
                            <SkeletonCheckboxBlock />
                          ) : (
                            <Checkbox
                              title="Select all instances"
                              checked={checkIsAllSelected?.()}
                              onCmInput={onSelectAll}
                              disabled={state !== 'content'}
                            />
                          ))}

                        <ColumnHeader
                          label={content}
                          sortKey={sortKey}
                          isDefault={isDefault}
                          disabled={state !== 'content' || isDisabled}
                          showExtraPadding={showExtraPadding}
                          paddingWidth={paddingWidth}
                          onSort={onSort}
                        />
                      </>
                    </TH>
                  );
                })}
              </TRHeader>
            </THead>
            {state === 'skeleton' && skeletonColumns !== undefined && (
              <Skeleton columns={skeletonColumns} />
            )}
            {state === 'empty' && (
              <Message type="empty">{emptyMessage}</Message>
            )}
            {state === 'error' && <Message type="error" />}

            <InfiniteScroller
              onVerticalScrollStartReach={onVerticalScrollStartReach}
              onVerticalScrollEndReach={onVerticalScrollEndReach}
              scrollableContainerRef={scrollableContentRef}
            >
              <tbody data-testid="data-list">
                {rows.map(
                  ({id, ariaLabel, content, checkIsSelected, onSelect}) => {
                    const isSelected = checkIsSelected?.();

                    return (
                      <Row
                        key={id}
                        id={id}
                        ariaLabel={ariaLabel}
                        content={content}
                        selectionType={selectionType}
                        onSelect={onSelect}
                        isSelected={isSelected}
                      />
                    );
                  }
                )}
              </tbody>
            </InfiniteScroller>
          </Table>
        </ScrollableContent>
      </List>
    );
  }
);
export {SortableTable};
