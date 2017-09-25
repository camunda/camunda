import React from 'react';
import moment from 'moment';

const jsx = React.createElement;

export class DateInput extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      stringDate: this.props.date.format(this.props.format),
      error: false
    };
  }

  componentWillReceiveProps(newProps) {
    const newStringDate = newProps.date.format(this.props.format);

    // prevents unnecessary state change
    if (this.state.stringDate !== newStringDate) {
      this.setState({
        ...this.state,
        stringDate: newStringDate
      });
    }
  }

  render() {
    return <input type="text"
                  className={this.props.className + (this.state.error ? ' error' : '')}
                  value={this.state.stringDate}
                  onChange={this.onInputChange} />;
  }

  onInputChange = event => {
    const value = event.target.value;
    const date = moment(value, this.props.format);
    const isValid = date.isValid() && date.format(this.props.format) === value;

    if (!date.isSame(this.props.date) && isValid) {
      this.props.onDateChange(date);
    }

    this.setState({
      stringDate: value,
      error: !isValid
    });
  }
}
