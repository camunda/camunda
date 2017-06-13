import {jsx, OnEvent, Match, Case, Default, Text, withSelector, Class} from 'view-utils';
import {removeHighlights, addHighlight, unsetElement} from './service';
import {ControlsElement} from 'main/processDisplay/controls';

export const AnalysisInput = withSelector(({name}) => {
  return <ControlsElement name={name}>
    <ul className="list-group">
      <li className="list-group-item" style="padding: 6px; cursor: default;">
        <OnEvent event="mouseover" listener={hover} />
        <OnEvent event="mouseout" listener={unhover} />
        <Match>
          <Case predicate={isSelected}>
            <span>
              <button type="button" className="btn btn-link btn-xs pull-right" style="padding-top: 0; padding-bottom: 0;">
                <OnEvent event="click" listener={unset} />
                ×
              </button>
              <Text property="name" />
            </span>
          </Case>
          <Default>
            <span>Please Select <Text property="label" /></span>
          </Default>
        </Match>
        <Class className="btn-highlight" selector="hovered" />
      </li>
    </ul>
  </ControlsElement>;

  function isSelected({name}) {
    return name;
  }

  function unset({state:{type}}) {
    unsetElement(type);
  }

  function hover({state: {type}}) {
    addHighlight(type);
  }

  function unhover() {
    removeHighlights();
  }
});
