import {jsx, createStateComponent, withSelector, Match, Case, Scope, Default, Text, OnEvent} from 'view-utils';
import {Filter, CreateFilter} from './filter';
import {ProcessDefinition, getDefinitionId} from './processDefinition';
import {unsetEndEvent, unsetGateway} from '../diagram/analytics/service';
import {View} from './view';

export const Controls = withSelector(({onCriteriaChanged, getBpmnViewer}) => {
  const State = createStateComponent();

  return <State>
    <div className="controls row">
      <div className="col-xs-12">
        <form>
          <table>
            <tbody>
              <tr>
                <td><label>Process Definition</label></td>
                <td><label>View</label></td>
                <td colspan="2"><label>Filter</label></td>
                <Match>
                  <Case predicate={isBranchAnalyisView}>
                    <td><label>End Event</label></td>
                    <td><label>Gateway</label></td>
                  </Case>
                </Match>
              </tr>
            <tr>
              <Scope selector="controls">
                <ProcessDefinition selector="processDefinition" onProcessDefinitionSelected={onControlsChange} />
                <View />
                <Filter onFilterDeleted={onControlsChange} />
                <CreateFilter onFilterAdded={onControlsChange} />
              </Scope>
              <Match>
                <Case predicate={isBranchAnalyisView}>
                  <Scope selector={getAnalysisSelection}>
                    <td>
                      <ul className="list-group">
                        <li className="list-group-item" style="padding: 6px;">
                          <Match>
                            <Case predicate={has('endEvent')}>
                              <span>
                                <button type="button" className="btn btn-link btn-xs pull-right">
                                  <OnEvent event="click" listener={unsetEndEvent} />
                                  ×
                                </button>
                                <Text property="endEvent" />
                              </span>
                            </Case>
                            <Default>
                              <span>Please Select End Event</span>
                            </Default>
                          </Match>
                        </li>
                      </ul>
                    </td>
                    <td>
                      <ul className="list-group">
                        <li className="list-group-item" style="padding: 6px;">
                        <Match>
                          <Case predicate={has('gateway')}>
                            <span>
                              <button type="button" className="btn btn-link btn-xs pull-right">
                                <OnEvent event="click" listener={unsetGateway} />
                                ×
                              </button>
                              <Text property="gateway" />
                            </span>
                          </Case>
                          <Default>
                            <span>Please Select Gateway</span>
                          </Default>
                        </Match>
                        </li>
                      </ul>
                    </td>
                  </Scope>
                </Case>
              </Match>
            </tr>
          </tbody>
          </table>
        </form>
      </div>
    </div>
  </State>;

  function getAnalysisSelection({display:{selection}}) {
    const out = {};

    Object.keys(selection).forEach(key => {
      out[key] = getName(selection[key]);
    });

    return out;
  }

  function getName(id) {
    if (id) {
      return getBpmnViewer()
      .get('elementRegistry')
      .get(id)
      .businessObject
      .name
      || id;
    }
  }

  function has(target) {
    return (selection) => {
      return selection[target];
    };
  }

  function isBranchAnalyisView({controls:{view}}) {
    return view === 'branch_analysis';
  }

  function onControlsChange() {
    const {controls: {processDefinition, filter: query}} = State.getState();

    const criteria = {
      definition: getDefinitionId(processDefinition),
      query
    };

    onCriteriaChanged(criteria);
  }
});
