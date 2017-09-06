import {
  jsx, withSelector, Match, Case, Default, Scope, List, dispatchAction,
  createStateComponent
} from 'view-utils';
import {LoadingIndicator} from 'widgets';
import {loadProcessDefinitions} from './service';
import {isLoaded, runOnce, addDestroyEventCleanUp} from 'utils';
import {PreviewCard} from './PreviewCard';
import {LOADING_PROPERTY} from './reducer';

export const ProcessSelection = withSelector(() => {
  return (parentNode, eventsBus) => {
    const State = createStateComponent();
    const template = <State>
      <div className="process-selection">
        <h3>Select a Process Definition:</h3>
        <LoadingIndicator predicate={isLoading}>
          <Match>
            <Case predicate={areThereNoProcessDefinitions}>
              <div className="no-definitions">
                <span className="indicator glyphicon glyphicon-info-sign"></span>
                <div className="title">No Process Definitions</div>
                <div className="text"><a href="https://docs.camunda.org/optimize/">Find out how to import your data</a></div>
              </div>
            </Case>
            <Default>
              <Scope selector={({processDefinitions: {data: {list}}}) => list}>
                <div className="row">
                  <List>
                    <Scope selector={getCardState}>
                      <PreviewCard />
                    </Scope>
                  </List>
                </div>
              </Scope>
            </Default>
          </Match>
        </LoadingIndicator>
      </div>
    </State>;
    const templateUpdate = template(parentNode, eventsBus);

    addDestroyEventCleanUp(eventsBus, dispatchAction, LOADING_PROPERTY);

    return [templateUpdate, runOnce(loadProcessDefinitions)];

    function isLoading({processDefinitions}) {
      return !isLoaded(processDefinitions);
    }

    function areThereNoProcessDefinitions({processDefinitions: {data: {list}}}) {
      return list.length === 0;
    }

    function getCardState({current, versions}) {
      const {processDefinitions: {data: {engineCount}}} = State.getState();

      return {
        current,
        versions,
        engineCount
      };
    }
  };
});
