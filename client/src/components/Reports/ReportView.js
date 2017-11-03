import React from 'react';

import './ReportView.css';

export default class ReportView extends React.Component {
  render() {
    return (<textarea readOnly value={JSON.stringify(this.props.data, null, 2)} className='ReportView__textarea' />);
  }
}
