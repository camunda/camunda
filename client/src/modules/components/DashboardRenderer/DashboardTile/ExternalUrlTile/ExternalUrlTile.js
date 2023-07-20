/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import './ExternalUrlTile.scss';

export default class ExternalUrlTile extends React.Component {
  state = {
    reloadState: 0,
  };

  reloadTile = () => {
    this.setState({reloadState: this.state.reloadState + 1});
  };

  render() {
    const {tile, children = () => {}} = this.props;

    if (tile.configuration && tile.configuration.external) {
      return (
        <div className="ExternalUrlTile DashboardTile__wrapper">
          <iframe
            key={this.state.reloadState}
            title="External URL"
            src={tile.configuration.external}
            frameBorder="0"
            style={{width: '100%', height: '100%'}}
          />
          {children({loadTileData: this.reloadTile})}
        </div>
      );
    }
  }
}

ExternalUrlTile.isExternalUrlTile = function (tile) {
  return !!tile.configuration?.external;
};
