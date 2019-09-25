/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import {Icon} from 'components';

import EntityList from './EntityList';

import {loadEntities} from './service';

import './Home.scss';

export default withErrorHandling(
  class Home extends React.Component {
    state = {
      entities: null
    };

    componentDidMount() {
      this.loadList();
    }

    loadList = () => {
      this.props.mightFail(
        loadEntities(),
        entities => this.setState({entities}),
        error => {
          showError(error);
          this.setState({entities: []});
        }
      );
    };

    render() {
      return (
        <div className="Home">
          <EntityList data={this.state.entities} onChange={this.loadList}>
            <div className="data-hint">
              <Icon type="hint" size="14" /> {t('home.data-hint')}
            </div>
          </EntityList>
        </div>
      );
    }
  }
);
