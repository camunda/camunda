import {jsx, withSelector, Text, Scope, Match, Case} from 'view-utils';
import {loadProgress} from './service';

export const Progress = withSelector(() => {
  const template = <Scope selector={({data}) => data || {} }>
    <Match>
      <Case predicate={({progress}) => progress !== undefined && progress < 100}>
        <div className="import-progress">
          Import in progress <Text property="progress" />%
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
