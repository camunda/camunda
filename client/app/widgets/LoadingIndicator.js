import {jsx, Match, Case, Default, Children} from 'view-utils';

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

export function Loader({className, style = 'position:static;'}) {
  return <div className={'loading_indicator overlay ' + className} style={style}>
    <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
    <div className="text">loading</div>
  </div>;
}
