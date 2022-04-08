/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import ProcessView from './ProcessView';
import ProcessEdit from './ProcessEdit';

import './Process.scss';

export default class Process extends React.Component {
  state = {
    redirect: null,
  };

  componentDidUpdate() {
    if (this.state.redirect) {
      this.setState({redirect: null});
    }
  }

  render() {
    const {viewMode, id} = this.props.match.params;
    const {redirect} = this.state;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="Process">
        {viewMode === 'edit' ? (
          <ProcessEdit id={id} onSave={(id) => this.setState({redirect: `../${id}/`})} />
        ) : (
          <ProcessView
            id={id}
            generated={viewMode === 'generated'}
            onDelete={() => this.setState({redirect: '../'})}
          />
        )}
      </div>
    );
  }
}
