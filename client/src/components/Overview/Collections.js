/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, EditCollectionModal} from 'components';
import CollectionItem from './subComponents/CollectionItem';
import ReportItem from './subComponents/ReportItem';

import './Collections.scss';
import DashboardItem from './subComponents/DashboardItem';

import {withStore} from './OverviewStore';
import {filterEntitiesBySearch} from './service';
import {t} from 'translation';

class Collections extends React.Component {
  state = {
    showAllId: null
  };

  isDashboard = entity => {
    return entity.reports;
  };

  render() {
    const {updating, collections, searchQuery} = this.props.store;

    const empty = collections.length === 0 && (
      <div className="collectionBlankSlate">
        <strong>{t('home.collection.empty')}</strong> <br />
        <Button
          variant="link"
          className="createLink"
          onClick={() => this.props.setCollectionToUpdate({})}
        >
          {t('home.collection.create')}
        </Button>
      </div>
    );

    const filteredCollections = filterEntitiesBySearch(collections, searchQuery);

    const noSearchResult = !empty && filteredCollections.length === 0 && (
      <p className="empty">{t('home.collection.noSearchResult', {searchQuery})}</p>
    );

    return (
      <div className="Collections">
        <ul className="entityList">
          {empty}
          {noSearchResult}
          {filteredCollections.map(collection => (
            <CollectionItem
              key={collection.id}
              collection={collection}
              setCollectionToUpdate={this.props.setCollectionToUpdate}
              showDeleteModalFor={this.props.showDeleteModalFor}
            >
              {collection.data.entities.length > 0 ? (
                <ul className="entityList">
                  {collection.data.entities
                    .slice(0, this.state.showAllId === collection.id ? undefined : 5)
                    .map(entity =>
                      this.isDashboard(entity) ? (
                        <DashboardItem key={entity.id} dashboard={entity} collection={collection} />
                      ) : (
                        <ReportItem key={entity.id} report={entity} collection={collection} />
                      )
                    )}
                </ul>
              ) : (
                <p className="empty">{t('home.collection.noItems')}</p>
              )}
              <div className="showAll">
                {!this.state.loading &&
                  collection.data.entities.length > 5 &&
                  (this.state.showAllId !== collection.id ? (
                    <>
                      {collection.data.entities.length} {t('home.collection.Items')}.{' '}
                      <Button
                        variant="link"
                        onClick={() => this.setState({showAllId: collection.id})}
                      >
                        {t('common.entity.showAll')}
                      </Button>
                    </>
                  ) : (
                    <Button variant="link" onClick={() => this.setState({showAllId: null})}>
                      {t('common.entity.showLess')}
                    </Button>
                  ))}
              </div>
            </CollectionItem>
          ))}
        </ul>
        {updating && (
          <EditCollectionModal
            collection={updating}
            onClose={() => this.props.setCollectionToUpdate(null)}
            onConfirm={this.props.updateOrCreateCollection}
          />
        )}
      </div>
    );
  }
}

export default withStore(Collections);
