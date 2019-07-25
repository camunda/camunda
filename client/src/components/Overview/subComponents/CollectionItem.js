/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon} from 'components';
import classnames from 'classnames';

import entityIcons from '../entityIcons';
import LastModified from './LastModified';
import {t} from 'translation';

const EntityIcon = entityIcons.collection.generic.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

export default class CollectionItem extends React.Component {
  state = {
    exanded: false
  };

  toggleExpanded = evt => {
    this.setState({expanded: !this.state.expanded});
  };

  render() {
    const {
      collection: {
        id,
        name,
        created,
        owner,
        data: {entities}
      },
      setCollectionToUpdate,
      showDeleteModalFor,
      children
    } = this.props;

    return (
      <li className="CollectionItem">
        <div className="listItem">
          <Button className="info ToggleCollapse" onClick={this.toggleExpanded}>
            <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state.expanded})} />
            <span className="icon">
              <EntityIcon />
            </span>
            <div className="textInfo">
              <div className="dataTitle">
                <h2>{name}</h2>
              </div>
              <div className="extraInfo">
                <div className="custom">
                  <span>
                    {entities.length}{' '}
                    {entities.length !== 1 ? t('home.collection.Items') : t('home.collection.Item')}
                  </span>
                </div>
                <LastModified label={t('common.entity.created')} date={created} author={owner} />
              </div>
            </div>
          </Button>
          <div className="collections" />
          <div className="operations">
            <Button
              title={t('home.collection.edit')}
              onClick={() => setCollectionToUpdate({id, name})}
            >
              <Icon type="edit" className="editLink" />
            </Button>
            <Button
              title={t('home.collection.delete')}
              onClick={showDeleteModalFor({type: 'collection', entity: {id, name}})}
            >
              <Icon type="delete" className="deleteIcon" />
            </Button>
          </div>
        </div>
        {this.state.expanded && children}
      </li>
    );
  }
}
