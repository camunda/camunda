import {jsx, withSelector, Text, Match, Case, Default, Scope} from 'view-utils';
import {Icon} from 'widgets/Icon';
import {Tooltip} from 'widgets/Tooltip';
import {loadProgress} from './service';

export const Progress = withSelector(() => {
  const template = <Scope selector="data">
    <Match>
      <Case predicate={({progress} = {}) => progress !== undefined && progress < 100}>
        <div className="import-progress">
          Import in progress <Text property="progress" />%
          <span className="connection-indicators">
            <Indicator/>
          </span>
        </div>
      </Case>
    </Match>
  </Scope>;

  loadProgress();

  return (node, eventsBus) => {
    return [
      template(node, eventsBus)
    ];
  };
});

function Indicator() {
  return <Match>
    <Case predicate={isConnected}>
      <Icon icon="ok-sign">
        <Tooltip>
          Connected to engine and elastic search
        </Tooltip>
      </Icon>
    </Case>
    <Default>
      <Scope selector={getErrorMessage}>
        <Icon icon="minus-sign">
          <Tooltip>
            <Text />
          </Tooltip>
        </Icon>
      </Scope>
    </Default>
  </Match>;

  function isConnected({connectedToElasticsearch, connectedToEngine}) {
    return connectedToElasticsearch && connectedToEngine;
  }

  function getErrorMessage({connectedToElasticsearch, connectedToEngine}) {
    if (!connectedToElasticsearch && !connectedToEngine) {
      return 'Disconnected from elastic search and engine';
    }

    if (!connectedToEngine) {
      return 'Disconnected from engine';
    }

    if (!connectedToElasticsearch) {
      return 'Disconnected from elastic search';
    }
  }
}
