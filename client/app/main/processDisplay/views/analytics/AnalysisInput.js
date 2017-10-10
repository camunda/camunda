import React from 'react';
const jsx = React.createElement;

import {removeHighlights, addHighlight, unsetElement} from './service';

export class AnalysisInput extends React.Component {
  render() {
    const {name, selection} = this.props;

    if (!selection) {
      return null;
    }

    //TODO: use the reactified ControlsElement component
    return (
      <div className="controls-element">
        <div className="controls-element-name">{name}</div>
        <div className="controls-element-body">
          <ul className="list-group">
            <li className={'list-group-item' + (selection.hovered ? ' btn-highlight' : '')} onMouseOver={this.hover} onMouseOut={this.unhover} style={{padding: '6px', cursor: 'default'}}>
              {
                selection.name && (
                <span>
                  {selection.name}
                  <button type="button" className="btn btn-link btn-xs" style={{paddingTop: '0', paddingBottom: '0'}} onClick={this.unset}>
                    ×
                  </button>
                </span>
                ) || (
                  <span>Please Select {selection.label}</span>
                )
              }
            </li>
          </ul>
        </div>
      </div>
    );
  }

  unset = evt => {
    unsetElement(this.props.selection.type);
  }

  hover = () => {
    addHighlight(this.props.selection.type);
  }

  unhover = () => {
    removeHighlights();
  }
}
