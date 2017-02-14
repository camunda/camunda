import {jsx, withSelector, Match, Case, Default} from 'view-utils';
import {HeatmapDiagram} from './diagram';
import {ProcessDefinition, Filter, CreateFilter, Result, View} from './controls';
import {isInitial, isLoading} from 'utils/loading';
import {loadDiagram, loadHeatmap} from './service';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const template = <div className="process-display">
    <div className="controls row">
      <div className="col-xs-12">
        <form>
          <table>
            <tbody>
              <tr>
                <td><label>Process Definition</label></td>
                <td><label>View</label></td>
                <td colspan="2"><label>Filter</label></td>
                <td><label>Result</label></td>
              </tr>
            <tr>
              <ProcessDefinition selector="processDefinition" />
              <View />
              <Filter />
              <CreateFilter />
              <Result />
            </tr>
          </tbody>
          </table>
        </form>
      </div>
    </div>
    <div className="diagram">
      <Match>
        <Case predicate={isLoadingSomething}>
          <div className="loading_indicator overlay">
            <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
            <div className="text">loading</div>
          </div>
        </Case>
        <Case predicate={noProcessDefinitions}>
          <div className="help_screen overlay">
            <div className="no_definitions">
              <span className="indicator glyphicon glyphicon-info-sign"></span>
              <div className="title">No Process Definitions</div>
              <div className="text"><a href="https://github.com/camunda/camunda-optimize/wiki/Installation-guide">Find out how to import your data</a></div>
            </div>
          </div>
        </Case>
        <Case predicate={noDefinitionSelected}>
          <div className="help_screen overlay">
            <div className="process_definition">
              <span className="indicator glyphicon glyphicon-chevron-up"></span>
              <div>Select Process Defintion</div>
            </div>
          </div>
        </Case>
        <Default>
          <HeatmapDiagram selector="display"/>
        </Default>
      </Match>
    </div>
  </div>;

  function isLoadingSomething({display: {diagram, heatmap}, processDefinition: {availableProcessDefinitions}}) {
    return isLoading(diagram) || isLoading(heatmap) || isLoading(availableProcessDefinitions);
  }

  function noProcessDefinitions({processDefinition}) {
    const data = processDefinition.availableProcessDefinitions.data;

    return data && data.length === 0;
  }

  function noDefinitionSelected({processDefinition}) {
    return !processDefinition.selected;
  }

  function filterValid({processDefinitionId}) {
    return !!processDefinitionId;
  }

  function getFilter({processDefinition, filter}) {
    const dateFilterArray = [];

    filter.query.forEach(entry => {
      dateFilterArray.push({
        type: 'start_date',
        operator: '>=',
        value : entry.data.start,
        lowerBoundary : true,
        upperBoundary : true
      });

      dateFilterArray.push({
        type: 'start_date',
        operator: '<=',
        value : entry.data.end,
        lowerBoundary : true,
        upperBoundary : true
      });
    });

    return {
      processDefinitionId: processDefinition.selected,
      filter: {
        dates: dateFilterArray
      }
    };
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, (state) => {
      const filter = getFilter(state);

      if (filterValid(filter)) {
        if (isInitial(state.display.diagram)) {
          loadDiagram(filter);
        }
        if (isInitial(state.display.heatmap)) {
          loadHeatmap(filter);
        }
      }
    }];
  };
}
