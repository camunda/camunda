import {jsx, withSelector, Text, Match, Case} from 'view-utils';
import {loadProgress} from './service';

export const Progress = withSelector(() => {
  const template = <Match>
    <Case predicate={({data}) => data !== undefined && data < 100}>
      <div className="import-progress">
        Import in progress <Text property="data" />%
      </div>
    </Case>
  </Match>;

  loadProgress();

  return (node, eventsBus) => {
    return [
      template(node, eventsBus)
    ];
  };
});
