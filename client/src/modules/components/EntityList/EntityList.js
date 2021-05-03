/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, Tooltip, Input} from 'components';

import SearchField from './SearchField';
import ListItem from './ListItem';

import './EntityList.scss';
import BulkMenu from './BulkMenu';

export default function EntityList({
  name,
  children,
  action,
  isLoading,
  data,
  empty,
  embedded,
  columns,
  sorting,
  reload,
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [scrolled, setScrolled] = useState(false);
  const [selected, setSelected] = useState([]);

  const entries = data || [];
  const entriesIds = entries
    .filter((entry) => entry.actions?.length > 0)
    .map((entry, idx) => entry.id || idx);
  const selectionMode = selected.length > 0;

  const matches = (value) =>
    typeof value == 'string' && value.toLowerCase().includes(searchQuery.toLowerCase());
  const searchFilteredData = entries.filter(({name, meta}) => matches(name) || meta.some(matches));

  const isEmpty = !isLoading && entries.length === 0;
  const hasResults = searchFilteredData.length > 0;
  const hasWarning = entries.some(({warning}) => warning);
  const hasSingleAction = entries.every(({actions}) => !actions || actions.length <= 1);
  const hasSorting = columns?.some((config) => config.key);

  return (
    <div
      className={classnames('EntityList', {scrolled, embedded, selectionMode})}
      onScroll={(evt) => setScrolled(evt.target.scrollTop > 0)}
    >
      <div className="header">
        <div className="titleBar">
          <h1>{name}</h1>
          {!embedded && <SearchField value={searchQuery} onChange={setSearchQuery} />}
          {hasSorting && (
            <Dropdown icon label={<Icon type="sort-menu" />} className="sortMenu">
              {columns
                .filter((config) => typeof config === 'object')
                .map(({name, key, defaultOrder}) => (
                  <Dropdown.Option
                    checked={sorting?.key === key}
                    key={key}
                    onClick={() => reload(key, defaultOrder)}
                  >
                    {name}
                  </Dropdown.Option>
                ))}
            </Dropdown>
          )}
          <BulkMenu
            selectedEntries={entries
              .filter((entity) => selected.includes(entity.id))
              .map(({id, entityType}) => ({id, entityType}))}
            setSelected={setSelected}
            reload={reload}
          />
          <div className="action">{action}</div>
        </div>
        {columns && hasResults && (
          <div className="columnHeaders">
            <Input
              selected={selected}
              onClick={(evt) => evt.stopPropagation()}
              type="checkbox"
              checked={selected.length === entriesIds.length}
              onChange={({target: {checked}}) =>
                checked
                  ? setSelected([...new Set([...selected, ...entriesIds])])
                  : setSelected(selected.filter((id) => !entriesIds.includes(id)))
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
                    reload(
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
                selected={selected.includes(data.id)}
                onSelectionChange={(evt) => {
                  evt.target.checked
                    ? setSelected([...selected, data.id])
                    : setSelected(selected.filter((id) => id !== data.id));
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
