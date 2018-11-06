import React from 'react';
import {Switch} from 'components';

export default class Table extends React.Component {
  updateProp = evt => {
    this.props.onChange('customProp')(evt.target.checked);
  };

  render() {
    return (
      <div>
        <p>I am a table!</p>
        <Switch
          type="checkbox"
          onChange={this.updateProp}
          checked={!!this.props.configuration.customProp}
        />{' '}
        Custom Prop
      </div>
    );
  }
}

Table.defaults = {
  customProp: false
};
