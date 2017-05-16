import {jsx, withSelector, Text, Match, Case, Default, Scope} from 'view-utils';
import {Icon} from 'widgets/Icon';
import {Tooltip} from 'widgets/Tooltip';
import {loadProgress} from './service';

const Indicator = withSelector(({ok, fail}) => {
  return <Match>
    <Case predicate={state => state}>
      <Icon icon="glyphicon-ok-sign">
        <Tooltip text={ok} />
      </Icon>
    </Case>
    <Default>
      <Icon icon="glyphicon-minus-sign">
        <Tooltip text={fail} />
      </Icon>
    </Default>
  </Match>;
});

export const Progress = withSelector(() => {
  const template = <Scope selector="data">
    <Match>
      <Case predicate={({progress} = {}) => progress !== undefined && progress < 100}>
        <div className="import-progress">
          Import in progress <Text property="progress" />%
          <span className="connection-indicators">
            <Indicator selector="connectedToElasticsearch" ok="Connected to elasticsearch" fail="Couldn't connect to elasticsearch"/>
            <Indicator selector="connectedToEngine" ok="Connected to engine" fail="Couldn't connect to engine"/>
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
