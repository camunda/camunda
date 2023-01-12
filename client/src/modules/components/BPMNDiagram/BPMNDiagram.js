/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import Modeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';
import disableCollapsedSubprocessModule from 'bpmn-js-disable-collapsed-subprocess';
import elementTemplateIconExtension from '@bpmn-io/element-templates-icons-renderer';

import {withErrorHandling} from 'HOC';
import {themed} from 'theme';
import ZoomControls from './ZoomControls';
import {LoadingIndicator} from 'components';
import classnames from 'classnames';

import './BPMNDiagram.scss';

const availableViewers = [];

export default themed(
  withErrorHandling(
    class BPMNDiagram extends React.Component {
      constructor(props) {
        super(props);

        this.state = {
          loaded: false,
        };
        this.resizeObserver = new ResizeObserver(this.fitDiagram);
      }

      storeContainer = (container) => {
        this.container = container;
      };

      render() {
        const {loading, style, xml, children, emptyText, disableNavigation} = this.props;
        const {loaded} = this.state;

        return (
          <div
            className={classnames('BPMNDiagram', {loading: loading, disableNavigation})}
            style={style}
            ref={this.storeContainer}
          >
            {loaded &&
              xml &&
              children &&
              React.Children.map(children, (child) =>
                React.cloneElement(child, {viewer: this.viewer})
              )}
            {loaded && xml && <ZoomControls zoom={this.zoom} fit={this.fitDiagram} />}
            {(!loaded || loading) && <LoadingIndicator className="diagramLoading" />}
            {!xml && emptyText && <div className="emptyText">{emptyText}</div>}
          </div>
        );
      }

      zoom = (val) => {
        this.viewer.get('zoomScroll').stepZoom(val);
      };

      componentDidUpdate(prevProps) {
        if (
          (prevProps.xml !== this.props.xml && !this.props.allowModeling) ||
          prevProps.theme !== this.props.theme
        ) {
          this.unattach(prevProps.xml, prevProps.theme);
          this.importXML(this.props.xml);
        }

        // The diagram will not be mounted at the beggining when refreshing the page
        // because we hide the the content of the privateRoute untill the page is loaded
        // Therefore, we need to try to fit the diagram again after the component updates
        if (isNaN(this.viewer?.get('canvas').viewbox().width)) {
          this.fitDiagram();
        }
      }

      unattach = (xml, theme) => {
        if (this.viewer && this.viewer.constructor !== Modeler) {
          this.viewer.detach();
          availableViewers.push({
            viewer: this.viewer,
            disableNavigation: this.props.disableNavigation,
            theme,
            xml,
          });
          this.viewer = null;
        }
      };

      findOrCreateViewerFor = async (xml, theme) => {
        const idx = availableViewers.findIndex(
          (conf) =>
            conf.xml === xml &&
            conf.theme === theme &&
            conf.disableNavigation === this.props.disableNavigation
        );

        const additionalModules = [
          elementTemplateIconExtension,
          {drilldownOverlayBehavior: ['value', null]},
        ];
        const available = availableViewers[idx];

        if (!this.props.allowModeling && available) {
          availableViewers.splice(idx, 1);
          return available.viewer;
        }

        let Constructor = NavigatedViewer;
        if (this.props.disableNavigation) {
          Constructor = Viewer;
        } else if (this.props.allowModeling) {
          Constructor = Modeler;
          additionalModules.push(disableCollapsedSubprocessModule);
        }

        const viewer = new Constructor({
          canvas: {
            deferUpdate: false,
          },
          keyboard: {bindTo: document},
          bpmnRenderer: getDiagramColors(theme),
          additionalModules,
        });

        await viewer.importXML(xml);
        const defs = viewer._container.querySelector('defs');
        if (defs) {
          const highlightMarker = defs.querySelector('marker').cloneNode(true);

          highlightMarker.setAttribute('id', 'sequenceflow-end-highlight');

          defs.appendChild(highlightMarker);
        }

        return viewer;
      };

      importXML = (xml) => {
        if (xml) {
          this.setState({loaded: false});

          this.props.mightFail(this.findOrCreateViewerFor(xml, this.props.theme), (viewer) => {
            if (this.props.xml !== xml) {
              // The xml we are supposed to render has changed while the viewer was created
              // The viewer for the old XML must be discarded, otherwise multiple viewers
              // would be attached to the same container
              return;
            }

            this.viewer = viewer;
            this.viewer.attachTo(this.container);

            this.fitDiagram();

            this.setState({loaded: true});
          });
        } else {
          this.setState({loaded: true});
        }
      };

      componentDidMount() {
        this.importXML(this.props.xml);
        this.resizeObserver.observe(this.container);
      }

      componentWillUnmount() {
        this.unattach(this.props.xml, this.props.theme);
        this.resizeObserver.disconnect();
      }

      fitDiagram = () => {
        // if we resize the browsers window so that the diagram height is 0,
        // bpmn.js fit function crashes in firefox
        // that's why we need to check that the container height is greater that 0
        if (this.viewer && this.container.clientHeight > 0) {
          const canvas = this.viewer.get('canvas');

          canvas.resized();
          canvas.zoom('fit-viewport', 'auto');
        }
      };
    }
  )
);

function getDiagramColors(theme) {
  if (theme === 'dark') {
    return {
      defaultFillColor: '#313238',
      defaultStrokeColor: '#dedede',
    };
  } else {
    return {
      defaultFillColor: '#fdfdfe',
      defaultStrokeColor: '#333',
    };
  }
}
