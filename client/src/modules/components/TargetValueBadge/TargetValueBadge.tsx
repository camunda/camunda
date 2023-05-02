/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import BaseViewer from 'bpmn-js/lib/BaseViewer';

import {formatters, TimeObject} from 'services';

import './TargetValueBadge.scss';

const badgeType = 'TARGET_VALUE_BADGE';

export interface Overlay extends BaseViewer {
  add: (
    id: string,
    type: string,
    options: {
      position: {
        top: number;
        right: number;
      };
      show: {
        minZoom: number;
        maxZoom: number;
      };
      html: HTMLElement;
    }
  ) => void;
  remove: (options: {type: string}) => void;
}

interface TargetValueBadgeProps {
  viewer: BaseViewer;
  values: Record<string, TimeObject>;
}

export default function TargetValueBadge({values, viewer}: TargetValueBadgeProps) {
  useEffect(() => {
    return () => {
      viewer.get<Overlay>('overlays').remove({type: badgeType});
    };
  }, [viewer]);

  if (viewer) {
    const overlays = viewer.get<Overlay>('overlays');

    overlays.remove({type: badgeType});

    (Object.keys(values) as (keyof typeof values)[]).forEach((id) => {
      if (values[id] && values[id]?.value) {
        const container = document.createElement('div');
        container.innerHTML = `<span class="TargetValueBadge">${formatters.duration(
          values[id]
        )}</span>`;
        const overlayHtml = container.firstChild as HTMLElement;

        if (overlayHtml) {
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
      }
    });
  }

  return null;
}
