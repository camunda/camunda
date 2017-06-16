import {jsx, withSelector, Match, Case, Default, Scope, List, dispatchAction} from 'view-utils';
import {LoadingIndicator} from 'widgets';
import {loadProcessDefinitions} from './service';
import {isLoaded, runOnce, addDestroyEventCleanUp} from 'utils';
import {PreviewCard} from './PreviewCard';
import {LOADING_PROPERTY} from './reducer';

export const ProcessSelection = withSelector(() => {
  const template = <div className="process-selection">
    <h3>Select a Process Definition:</h3>
    <LoadingIndicator predicate={isLoading}>
      <Match>
        <Case predicate={areThereNoProcessDefinitions}>
          <div className="no-definitions">
            <span className="indicator glyphicon glyphicon-info-sign"></span>
            <div className="title">No Process Definitions</div>
            <div className="text"><a href="https://github.com/camunda/camunda-optimize-docs/blob/master/installation.md">Find out how to import your data</a></div>
          </div>
        </Case>
        <Default>
          <Scope selector={({processDefinitions: {data}}) => data}>
            <div className="row">
              <List>
                <PreviewCard />
              </List>
            </div>
          </Scope>
        </Default>
      </Match>
    </LoadingIndicator>
  </div>;

  function isLoading({processDefinitions}) {
    return !isLoaded(processDefinitions);
  }

  function areThereNoProcessDefinitions({processDefinitions: {data}}) {
    return data.length === 0;
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    addDestroyEventCleanUp(eventsBus, dispatchAction, LOADING_PROPERTY);

    return [templateUpdate, runOnce(loadProcessDefinitions)];
  };
});
