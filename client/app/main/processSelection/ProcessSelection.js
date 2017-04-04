import {jsx, withSelector, Match, Case, Default, Scope, List} from 'view-utils';
import {LoadingIndicator} from 'widgets';
import {loadProcessDefinitions} from './service';
import {isLoaded, runOnce} from 'utils';
import {PreviewCard} from './PreviewCard';

export const ProcessSelection = withSelector(() => {
  const template = <div className="process-selection">
    <h3>Select a Process Definition:</h3>
    <LoadingIndicator predicate={isLoading}>
      <Match>
        <Case predicate={areThereNoProcessDefinitions}>
          <div className="no-definitions">
            <span className="indicator glyphicon glyphicon-info-sign"></span>
            <div className="title">No Process Definitions</div>
            <div className="text"><a href="https://github.com/camunda/camunda-optimize/wiki/Installation-guide">Find out how to import your data</a></div>
          </div>
        </Case>
        <Default>
          <Scope selector={getGroupedDefinitions}>
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

  function areThereNoProcessDefinitions({processDefinitions:{data}}) {
    return data.length === 0;
  }

  function getGroupedDefinitions({processDefinitions:{data}, versions}) {
    const out = data
      .filter(entry => isSelectedVersion(entry, data, versions[entry.key]))
      .sort(({key: keyA}, {key: keyB}) => keyA.localeCompare(keyB))
      .map(definition => {
        return {
          ...definition,
          versions: getVersionsFor(definition, data)
        };
      });

    return out;
  }

  function getVersionsFor(entry, data) {
    return data
      .filter(({key}) => key === entry.key)
      .sort(({version: versionA}, {version: versionB}) => versionB - versionA);
  }

  function isSelectedVersion(entry, data, selectedVersion) {
    return entry.version === (selectedVersion ? selectedVersion : Math.max(...data
      .filter(({key}) => key === entry.key)
      .map(({version}) => version))
    );
  }

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, runOnce(loadProcessDefinitions)];
  };
});
