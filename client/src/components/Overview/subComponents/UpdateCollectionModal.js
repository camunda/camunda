import React, {Component} from 'react';
import {Button, Input, Modal} from 'components';

export default class UpdateCollectionModal extends Component {
  constructor(props) {
    super(props);
    const collection = props.collection && props.collection.name;
    this.state = {
      name: collection || 'New Collection'
    };
  }

  inputRef = input => {
    this.nameInput = input;
  };

  componentDidMount() {
    if (this.nameInput) {
      this.nameInput.focus();
      this.nameInput.select();
    }
  }

  handleKeyPress = evt => {
    if (evt.key === 'Enter') {
      this.props.onConfirm({name: this.state.name});
    }
  };

  render() {
    return (
      <Modal open={true} onClose={this.props.onClose}>
        <Modal.Header>Create New Collection</Modal.Header>
        <Modal.Content>
          <Input
            type="text"
            ref={this.inputRef}
            style={{width: '100%'}}
            value={this.state.name}
            onChange={({target: {value}}) => this.setState({name: value})}
            onKeyDown={this.handleKeyPress}
            isInvalid={!this.state.name}
          />
        </Modal.Content>
        <Modal.Actions>
          <Button className="cancel" type="primary" onClick={this.props.onClose}>
            Cancel
          </Button>
          <Button
            type="primary"
            color="blue"
            className="confirm"
            disabled={!this.state.name}
            onClick={() => this.props.onConfirm({name: this.state.name})}
          >
            {this.props.collection.name ? 'Save' : 'Create Collection'}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
