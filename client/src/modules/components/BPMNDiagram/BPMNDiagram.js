/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';

import {withErrorHandling} from 'HOC';
import {themed} from 'theme';

import './BPMNDiagram.scss';
import ZoomControls from './ZoomControls';
import {LoadingIndicator} from 'components';

const availableViewers = [];

export default themed(
  withErrorHandling(
    class BPMNDiagram extends React.Component {
      state = {
        loaded: false
      };

      storeContainer = container => {
        this.container = container;
      };

      render() {
        return (
          <div className="BPMNDiagram" style={this.props.style} ref={this.storeContainer}>
            {this.state.loaded &&
              this.props.children &&
              React.Children.map(this.props.children, child =>
                React.cloneElement(child, {viewer: this.viewer})
              )}
            {this.state.loaded && <ZoomControls zoom={this.zoom} fit={this.fitDiagram} />}
            {!this.state.loaded && <LoadingIndicator />}
          </div>
        );
      }

      zoom = val => {
        this.viewer.get('zoomScroll').stepZoom(val);
      };

      componentDidUpdate(prevProps) {
        if (prevProps.xml !== this.props.xml || prevProps.theme !== this.props.theme) {
          this.unattach(prevProps.xml, prevProps.theme);
          this.importXML(this.props.xml);
        }
      }

      unattach = (xml, theme) => {
        if (this.viewer) {
          this.viewer.detach();
          availableViewers.push({
            viewer: this.viewer,
            disableNavigation: this.props.disableNavigation,
            theme,
            xml
          });
          this.viewer = null;
        }
      };

      findOrCreateViewerFor = (xml, theme) => {
        const idx = availableViewers.findIndex(
          conf =>
            conf.xml === xml &&
            conf.theme === theme &&
            conf.disableNavigation === this.props.disableNavigation
        );

        const available = availableViewers[idx];

        if (available) {
          availableViewers.splice(idx, 1);
          return available.viewer;
        }

        const viewer = new (this.props.disableNavigation ? Viewer : NavigatedViewer)({
          canvas: {
            deferUpdate: false
          },
          bpmnRenderer: getDiagramColors(theme)
        });

        return new Promise(resolve => {
          viewer.importXML(xml, () => {
            const defs = viewer._container.querySelector('defs');
            const highlightMarker = defs.querySelector('marker').cloneNode(true);

            highlightMarker.setAttribute('id', 'sequenceflow-end-highlight');

            defs.appendChild(highlightMarker);
            resolve(viewer);
          });
        });
      };

      importXML = xml => {
        this.setState({loaded: false});

        this.props.mightFail(this.findOrCreateViewerFor(xml, this.props.theme), viewer => {
          this.viewer = viewer;
          this.viewer.attachTo(this.container);

          this.fitDiagram();

          this.setState({loaded: true});
        });
      };

      componentDidMount() {
        this.importXML(this.props.xml);

        const dashboardObject = this.container.closest('.DashboardObject');
        if (dashboardObject) {
          // if the diagram is on a dashboard, react to changes of the dashboard objects size
          new MutationObserver(this.fitDiagram).observe(dashboardObject, {attributes: true});
        }
      }

      componentWillUnmount() {
        this.unattach(this.props.xml);
      }

      fitDiagram = () => {
        const canvas = this.viewer.get('canvas');

        canvas.resized();
        canvas.zoom('fit-viewport', 'auto');
      };
    }
  )
);

function getDiagramColors(theme) {
  if (theme === 'dark') {
    return {
      defaultFillColor: '#313238',
      defaultStrokeColor: '#dedede'
    };
  } else {
    return {
      defaultFillColor: '#fdfdfe',
      defaultStrokeColor: '#333'
    };
  }
}
