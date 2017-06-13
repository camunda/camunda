import {jsx, Match, Case, Default, withSelector} from 'view-utils';
import {LoadingIndicator, createDiagram} from 'widgets';
import {isLoading} from 'utils';
import {getView} from 'main/processDisplay/controls/view';
import {createDefinitionCases} from './createDefinitionCases';
import {definitions} from './viewDefinitions';

export const ViewsDiagramArea =  withSelector(({isViewSelected}) => {
  const Diagram = createDiagram();

  return <div className="diagram">
    <LoadingIndicator predicate={isLoadingSomething}>
      <Match>
        <Case predicate={hasNoData}>
          <Diagram />
          <div className="no-data-indicator">
            No Data
          </div>
        </Case>
        {
          createDefinitionCases('Diagram', isViewSelected)
        }
        <Default>
          <Diagram />
        </Default>
      </Match>
    </LoadingIndicator>
  </div>;

  function isLoadingSomething({bpmnXml, heatmap, targetValue}) {
    return isLoading(bpmnXml) || isLoading(heatmap) || isLoading(targetValue);
  }

  function hasNoData(state) {
    const view = getView();
    const definition = definitions[view];

    return definition && definition.hasNoData(state);
  }
});
