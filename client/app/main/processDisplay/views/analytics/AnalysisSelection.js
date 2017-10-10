import React from 'react';
const jsx = React.createElement;

import {AnalysisInput} from './AnalysisInput';

export function createAnalysisSelection(getNameForElement) {
  return class AnalysisSelection extends React.Component {
    render() {
      return <div>
        <AnalysisInput name="End Event" selection={this.formatEndEvent(this.props)} />
        <AnalysisInput name="Gateway" selection={this.formatGateway(this.props)} />
      </div>;
    }

    formatSelection = type => ({selection, hover}) => {
      return selection && hover && {
        type: capitalize(type),
        label: capitalize(type.replace(/[A-Z]/g, ' $&')),
        name: getNameForElement(selection[type]),
        hovered: hover[type]
      };
    }

    formatEndEvent = this.formatSelection('EndEvent');
    formatGateway = this.formatSelection('Gateway');
  };
}

function capitalize(string) {
  return string.replace(/^./g, firstLetter => firstLetter.toUpperCase());
}
