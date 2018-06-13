import React, {Component} from 'react';
import Header from '../Header';

export default class Instance extends Component {
  render() {
    return (
      <Header
        active="detail"
        instances={14576}
        filters={9263}
        selections={24}
        incidents={328}
        detail="1234"
      />
    );
  }
}
