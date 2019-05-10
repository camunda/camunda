/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon, CollectionsDropdown} from 'components';
import LastModified from './LastModified';
import {Link} from 'react-router-dom';
import entityIcons from '../entityIcons';
import {withStore} from '../OverviewStore';

const EntityIcon = entityIcons.dashboard.generic.Component;

export default withStore(function DashboardItem({
  store: {collections},
  dashboard,
  collection,
  entitiesCollections,
  duplicateEntity,
  showDeleteModalFor,
  toggleEntityCollection,
  setCollectionToUpdate
}) {
  const dashboardCollections = entitiesCollections[dashboard.id];
  return (
    <li className="DashboardItem listItem">
      <Link className="info" to={`/dashboard/${dashboard.id}`}>
        <span className="icon">
          <EntityIcon />
        </span>
        <div className="textInfo">
          <div className="data dataTitle">
            <h3>{dashboard.name}</h3>
          </div>
          <div className="extraInfo">
            <span className="data custom">
              {dashboard.reports.length} Report
              {dashboard.reports.length !== 1 ? 's' : ''}
            </span>
            <LastModified
              label="Last changed"
              date={dashboard.lastModified}
              author={dashboard.lastModifier}
            />
          </div>
        </div>
      </Link>
      <div className="collections">
        <CollectionsDropdown
          entity={dashboard}
          currentCollection={collection}
          toggleEntityCollection={toggleEntityCollection}
          setCollectionToUpdate={setCollectionToUpdate}
          collections={collections}
          entityCollections={dashboardCollections}
        />
      </div>
      <div className="operations">
        <Link title="Edit Dashboard" to={`/dashboard/${dashboard.id}/edit`}>
          <Icon title="Edit Dashboard" type="edit" className="editLink" />
        </Link>
        <Button
          title="Duplicate Dashboard"
          onClick={duplicateEntity('dashboard', dashboard, collection)}
        >
          <Icon type="copy-document" title="Duplicate Dashboard" className="duplicateIcon" />
        </Button>
        <Button
          title="Delete Dashboard"
          onClick={showDeleteModalFor({
            type: 'dashboard',
            entity: dashboard
          })}
        >
          <Icon type="delete" title="Delete Dashboard" className="deleteIcon" />
        </Button>
      </div>
    </li>
  );
});
