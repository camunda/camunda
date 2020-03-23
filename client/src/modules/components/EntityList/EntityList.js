/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import classnames from 'classnames';

import {t} from 'translation';
import {LoadingIndicator} from 'components';

import SearchField from './SearchField';
import ListItem from './ListItem';

import './EntityList.scss';

export default function EntityList({name, children, action, isLoading, data, empty, embedded}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [scrolled, setScrolled] = useState(false);

  const entries = data || [];

  const searchFilteredData = entries.filter(({name}) =>
    name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const isEmpty = !isLoading && entries.length === 0;
  const hasResults = searchFilteredData.length > 0;
  const hasWarning = entries.some(({warning}) => warning);
  const hasSingleAction = entries.every(({actions}) => !actions || actions.length <= 1);

  return (
    <div
      className={classnames('EntityList', {scrolled, embedded})}
      onScroll={evt => setScrolled(evt.target.scrollTop > 0)}
    >
      <div className="header">
        <h1>{name}</h1>
        <SearchField value={searchQuery} onChange={setSearchQuery} />
        <div className="action">{action}</div>
      </div>
      <div className="content">
        {isLoading && <LoadingIndicator />}
        {isEmpty && <div className="empty">{empty}</div>}
        {!isLoading && !isEmpty && !hasResults && (
          <div className="empty">{t('common.notFound')}</div>
        )}
        {hasResults && (
          <ul>
            {searchFilteredData.map((data, idx) => (
              <ListItem
                key={idx}
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
