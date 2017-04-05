import {jsx, Match, Case, Default, Children} from 'view-utils';
import {Loader} from './Loader';

export function LoadingIndicator({predicate, children, floating = false}) {
  return <Match>
    <Case predicate={predicate}>
      <Loader />
    </Case>
    <Default>
      <Children children={children} />
    </Default>
  </Match>;
}
