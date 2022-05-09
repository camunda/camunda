/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {formatters} from 'services';

import './TargetValueBadge.scss';

const badgeType = 'TARGET_VALUE_BADGE';

export default class TargetValueBadge extends React.Component {
  componentWillUnmount() {
    this.props.viewer.get('overlays').remove({type: badgeType});
  }

  render() {
    const {viewer, values} = this.props;

    if (viewer) {
      const overlays = viewer.get('overlays');

      overlays.remove({type: badgeType});

      Object.keys(values).forEach((id) => {
        if (values[id] && values[id].value) {
          const container = document.createElement('div');
          container.innerHTML = `<span class="TargetValueBadge">${formatters.duration(
            values[id]
          )}</span>`;
          const overlayHtml = container.firstChild;

          // calculate overlay width
          document.body.appendChild(overlayHtml);
          const overlayWidth = overlayHtml.offsetWidth;

          document.body.removeChild(overlayHtml);

          overlays.add(id, badgeType, {
            position: {
              top: -14,
              right: overlayWidth - 11,
            },
            show: {
              minZoom: -Infinity,
              maxZoom: +Infinity,
            },
            html: overlayHtml,
          });
        }
      });
    }

    return null;
  }
}
