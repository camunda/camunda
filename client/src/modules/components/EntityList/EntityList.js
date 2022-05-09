/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, Tooltip, Input} from 'components';

import SearchField from './SearchField';
import ListItem from './ListItem';

import './EntityList.scss';

export default function EntityList({
  name,
  headerText,
  children,
  action,
  bulkActions,
  isLoading,
  data,
  empty,
  embedded,
  columns,
  sorting,
  onChange,
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [scrolled, setScrolled] = useState(false);
  const [selected, setSelected] = useState([]);

  const entries = data || [];

  const matches = (value) =>
    typeof value == 'string' && value.toLowerCase().includes(searchQuery.toLowerCase());
  const searchFilteredData = entries.filter(({name, meta}) => matches(name) || meta.some(matches));
  const filteredEntriesWithActions = searchFilteredData.filter(
    (entry) => entry.actions?.length > 0
  );

  const isEmpty = !isLoading && entries.length === 0;
  const hasResults = searchFilteredData.length > 0;
  const hasWarning = entries.some(({warning}) => warning);
  const hasSingleAction = entries.every(({actions}) => !actions || actions.length <= 1);
  const hasSorting = columns?.some((config) => config.key);

  useEffect(() => {
    setSelected([]);
  }, [data]);

  return (
    <div
      className={classnames('EntityList', {scrolled, embedded, selectionMode: selected.length > 0})}
      onScroll={(evt) => setScrolled(evt.target.scrollTop > 0)}
    >
      <div className="header">
        <div className="titleBar">
          <h1>{name}</h1>
          {headerText && <div className="headerText">{headerText}</div>}
          {!embedded && <SearchField value={searchQuery} onChange={setSearchQuery} />}
          {hasSorting && (
            <Dropdown icon label={<Icon type="sort-menu" />} className="sortMenu">
              {columns
                .filter((config) => typeof config === 'object')
                .map(({name, key, defaultOrder}) => (
                  <Dropdown.Option
                    checked={sorting?.key === key}
                    key={key}
                    onClick={() => onChange(key, defaultOrder)}
                  >
                    {name}
                  </Dropdown.Option>
                ))}
            </Dropdown>
          )}
          {bulkActions && (
            <div className="bulkMenu">
              {selected.length > 0 && (
                <Dropdown
                  main
                  primary
                  label={`${selected.length} ${t(
                    'common.itemSelected.' + (selected.length > 1 ? 'label-plural' : 'label')
                  )}`}
                >
                  {React.Children.map(bulkActions, (child, idx) =>
                    React.cloneElement(child, {
                      key: idx,
                      onDelete: onChange,
                      selectedEntries: selected,
                    })
                  )}
                </Dropdown>
              )}
            </div>
          )}
          <div className="action">{action?.(selected.length > 0)}</div>
        </div>
        {columns && hasResults && (
          <div className="columnHeaders">
            <Input
              className={classnames({hidden: !filteredEntriesWithActions.length})}
              type="checkbox"
              checked={filteredEntriesWithActions.every((entry) =>
                selected.some(({id}) => entry.id === id)
              )}
              onChange={({target: {checked}}) =>
                checked
                  ? setSelected(
                      selected.concat(
                        filteredEntriesWithActions.filter(
                          (entry) => !selected.some(({id}) => entry.id === id)
                        )
                      )
                    )
                  : setSelected(
                      selected.filter(
                        (entry) => !filteredEntriesWithActions.some(({id}) => entry.id === id)
                      )
                    )
              }
            />
            {columns
              .filter((config) => !config?.hidden)
              .map((titleOrConfig, idx) => {
                const title = titleOrConfig.name || titleOrConfig;
                const sortable = titleOrConfig.key;
                const sorted = sorting?.key && sorting.key === titleOrConfig.key;

                function changeSorting() {
                  if (sortable) {
                    onChange(
                      titleOrConfig.key,
                      sorted ? reverseOrder(sorting.order) : titleOrConfig.defaultOrder
                    );
                  }
                }

                return (
                  <Tooltip key={idx} content={title} overflowOnly>
                    <div
                      className={classnames({
                        name: idx === 0,
                        meta: idx !== 0,
                        sortable,
                        sorted,
                      })}
                    >
                      <span onClick={changeSorting}>{title}</span>
                      {sorted && (
                        <Icon type="sort-arrow" onClick={changeSorting} className={sorting.order} />
                      )}
                    </div>
                  </Tooltip>
                );
              })}
          </div>
        )}
        {isLoading && <LoadingIndicator />}
      </div>
      <div className="content">
        {isEmpty && <div className="empty">{empty}</div>}
        {!isLoading && !isEmpty && !hasResults && (
          <div className="empty">{t('common.notFound')}</div>
        )}
        {hasResults && (
          <ul className={classnames('itemsList', {isLoading})}>
            {searchFilteredData.map((data, idx) => (
              <ListItem
                key={idx}
                isSelected={selected.some((entry) => entry.id === data.id)}
                onSelectionChange={(evt) => {
                  evt.target.checked
                    ? setSelected([...selected, data])
                    : setSelected(selected.filter((entry) => entry.id !== data.id));
                }}
                data={data}
                hasWarning={hasWarning}
                singleAction={hasSingleAction}
              />
            ))}
          </ul>
        )}
        {children}
      </div>
    </div>
  );
}

function reverseOrder(order) {
  return order === 'asc' ? 'desc' : 'asc';
}
