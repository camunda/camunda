import React from 'react';
const jsx = React.createElement;

import {createViewUtilsComponentFromReact} from 'reactAdapter';

import {createDiagram} from 'widgets';
import {LoadingIndicator} from 'widgets/LoadingIndicator.react';
import {isLoading} from 'utils';
import {getView} from 'main/processDisplay/controls/view';
import {createDefinitionCases} from './createDefinitionCases';
import {definitions} from './viewDefinitions';

export class ViewsDiagramAreaReact extends React.Component {
  constructor(props) {
    super(props);

    this.diagram = createDiagram();
  }

  render() {
    const {views, isViewSelected} = this.props;
    const Diagram = this.diagram;

    if (!views) {
      return null;
    }

    return <div className="diagram">
      <LoadingIndicator loading={this.isLoadingSomething(views)}>
        {this.hasNoData(views) && (
          <div>
            <Diagram bpmnXml={views.bpmnXml} />
            <div className="no-data-indicator">
              No Data
            </div>
          </div>
        ) || (
          createDefinitionCases('Diagram', isViewSelected, views)
        ) || (
          <Diagram bpmnXml={views.bpmnXml} />
        )}
      </LoadingIndicator>
    </div>;
  }

  isLoadingSomething = ({bpmnXml, heatmap, targetValue}) => {
    return isLoading(bpmnXml) || isLoading(heatmap) || isLoading(targetValue);
  }

  hasNoData = state => {
    const view = getView();
    const definition = definitions[view];

    return definition && definition.hasNoData(state);
  }
}

export const ViewsDiagramArea = createViewUtilsComponentFromReact('div', ViewsDiagramAreaReact);
