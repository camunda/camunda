/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Link} from 'react-router-dom';

import {t} from 'translation';
import {LoadingIndicator, Icon, Input, Dropdown} from 'components';

import ListItem from './ListItem';

import './EntityList.scss';

export default function EntityList({name, children, action, isLoading, data, empty}) {
  const [searchQuery, setSearchQuery] = useState('');

  const searchFilteredData = (data || []).filter(({name}) =>
    name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const isEmpty = !isLoading && (data || []).length === 0;
  const hasResults = searchFilteredData.length > 0;

  return (
    <div className="EntityList">
      <div className="header">
        <h1>{name}</h1>
        <div className="searchContainer">
          <Icon className="searchIcon" type="search" />
          <Input
            required
            type="text"
            className="searchInput"
            placeholder={t('home.search.name')}
            value={searchQuery}
            onChange={({target: {value}}) => setSearchQuery(value)}
            onClear={() => setSearchQuery('')}
          />
        </div>
        <div className="action">{action}</div>
      </div>
      <div className="content">
        {isLoading && <LoadingIndicator />}
        {isEmpty && <div className="empty">{empty}</div>}
        {!isEmpty && !hasResults && <div className="empty">{t('common.notFound')}</div>}
        {hasResults && (
          <ul>
            {searchFilteredData.map(
              ({className, link, icon, type, name, meta1, meta2, meta3, action, actions}, idx) => {
                const content = (
                  <>
                    <ListItem.Section className="icon">{icon}</ListItem.Section>
                    <ListItem.Section className="name">
                      <div className="type">{type}</div>
                      <div className="entityName" title={name}>
                        {name}
                      </div>
                    </ListItem.Section>
                    <ListItem.Section className="meta1">{meta1}</ListItem.Section>
                    <ListItem.Section className="meta2">{meta2}</ListItem.Section>
                    <ListItem.Section className="meta3">{meta3}</ListItem.Section>
                  </>
                );

                return (
                  <ListItem key={idx} className={className} onClick={action}>
                    {link ? <Link to={link}>{content}</Link> : content}
                    {actions && actions.length && (
                      <div className="contextMenu" onClick={evt => evt.stopPropagation()}>
                        <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                          {actions.map(({action, icon, text}, idx) => (
                            <Dropdown.Option onClick={action} key={idx}>
                              <Icon type={icon} /> {text}
                            </Dropdown.Option>
                          ))}
                        </Dropdown>
                      </div>
                    )}
                  </ListItem>
                );
              }
            )}
          </ul>
        )}
        {children}
      </div>
    </div>
  );
}
