/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CSSProperties, Children, Component, ReactNode, cloneElement} from 'react';

import Modeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import Viewer from 'bpmn-js/lib/Viewer';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import disableCollapsedSubprocessModule from 'bpmn-js-disable-collapsed-subprocess';
import elementTemplateIconExtension from '@bpmn-io/element-templates-icons-renderer';
import classnames from 'classnames';

import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {themed, ThemeContextProps} from 'theme';
import {Loading} from 'components';
import {isReactElement} from 'services';

import ZoomControls from './ZoomControls';

import './BPMNDiagram.scss';

const availableViewers: {
  viewer: BaseViewer;
  disableNavigation?: boolean;
  theme: ThemeContextProps['theme'];
  xml: string | null;
}[] = [];

export interface ModdleElement {
  id: string;
  name?: string;
}

export type Canvas = {
  zoom: (fit: string, center?: string) => void;
  resized: () => void;
  viewbox: () => DOMRect;
  removeMarker: (id: string, selector: string) => void;
  addMarker: (id: string | ModdleElement, selector: string) => void;
};

type ZoomScroll = {
  stepZoom: (step: number) => void;
};

export interface BPMNDiagramProps extends WithErrorHandlingProps, ThemeContextProps {
  xml: string | null;
  loading?: boolean;
  style?: CSSProperties;
  children?: ReactNode;
  emptyText?: string;
  disableNavigation?: boolean;
  allowModeling?: boolean;
}

export interface BPMNDiagramState {
  loaded: boolean;
}

export class BPMNDiagram extends Component<BPMNDiagramProps, BPMNDiagramState> {
  resizeObserver: ResizeObserver;
  container?: HTMLDivElement;
  viewer?: BaseViewer | null;

  constructor(props: BPMNDiagramProps) {
    super(props);

    this.state = {
      loaded: false,
    };
    this.resizeObserver = new ResizeObserver(this.fitDiagram);
  }

  storeContainer = (container: HTMLDivElement) => {
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
          Children.map(
            children,
            (child) =>
              child &&
              isReactElement<{viewer: BaseViewer | null}>(child) &&
              cloneElement(child, {viewer: this.viewer})
          )}
        {loaded && xml && <ZoomControls zoom={this.zoom} fit={this.fitDiagram} />}
        {(!loaded || loading) && <Loading />}
        {!xml && emptyText && <div className="emptyText">{emptyText}</div>}
      </div>
    );
  }

  zoom = (val: number) => {
    this.viewer?.get<ZoomScroll>('zoomScroll').stepZoom(val);
  };

  componentDidUpdate(prevProps: BPMNDiagramProps) {
    if (
      (prevProps.xml !== this.props.xml && !this.props.allowModeling) ||
      prevProps.theme !== this.props.theme
    ) {
      this.unattach(prevProps.xml, prevProps.theme);
      this.importXML(this.props.xml);
    }

    // The diagram will not be mounted at the beggining when refreshing the page
    // because we hide the content of the privateRoute untill the page is loaded
    // Therefore, we need to try to fit the diagram again after the component updates
    if (!this.viewer || isNaN(this.viewer.get<Canvas>('canvas').viewbox().width)) {
      this.fitDiagram();
    }
  }

  unattach = (xml: string | null, theme: BPMNDiagramProps['theme']) => {
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

  findOrCreateViewerFor = async (xml: string, theme: BPMNDiagramProps['theme']) => {
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
      bpmnRenderer: getDiagramColors(theme),
      additionalModules,
    });

    await viewer.importXML(xml);
    // @ts-expect-error bpmn-js types are not correct
    const defs = viewer._container.querySelector('defs');
    if (defs) {
      const highlightMarker = defs.querySelector('marker').cloneNode(true);

      highlightMarker.setAttribute('id', 'sequenceflow-end-highlight');

      defs.appendChild(highlightMarker);
    }

    return viewer;
  };

  importXML = (xml: string | null) => {
    if (xml) {
      this.setState({loaded: false});

      this.props.mightFail(
        this.findOrCreateViewerFor(xml, this.props.theme),
        (viewer: BaseViewer) => {
          if (this.props.xml !== xml) {
            // The xml we are supposed to render has changed while the viewer was created
            // The viewer for the old XML must be discarded, otherwise multiple viewers
            // would be attached to the same container
            return;
          }

          this.viewer = viewer;
          if (this.container) {
            this.viewer.attachTo(this.container);
          }

          this.fitDiagram();

          this.setState({loaded: true});
        }
      );
    } else {
      this.setState({loaded: true});
    }
  };

  componentDidMount() {
    this.importXML(this.props.xml);
    if (this.container) {
      this.resizeObserver.observe(this.container);
    }
  }

  componentWillUnmount() {
    this.unattach(this.props.xml, this.props.theme);
    this.resizeObserver.disconnect();
  }

  fitDiagram = () => {
    // if we resize the browsers window so that the diagram height is 0,
    // bpmn.js fit function crashes in firefox
    // that's why we need to check that the container height is greater that 0
    if (this.viewer && this.container?.clientHeight && this.container.clientHeight > 0) {
      const canvas = this.viewer.get<Canvas>('canvas');

      canvas.resized();
      canvas.zoom('fit-viewport', 'auto');
    }
  };
}

export default themed(withErrorHandling(BPMNDiagram));

function getDiagramColors(theme: ThemeContextProps['theme']) {
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
