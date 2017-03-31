import {jsx, Match, Case, Default, Children} from 'view-utils';

export function LoadingIndicator({predicate, children, floating = false}) {
  return <Match>
    <Case predicate={predicate}>
      <div className="loading_indicator overlay" style="position:static;">
        <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
        <div className="text">loading</div>
      </div>
    </Case>
    <Default>
      <Children children={children} />
    </Default>
  </Match>;
}
