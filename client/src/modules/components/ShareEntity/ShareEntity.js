import React from 'react';
import {CopyToClipboard, Switch} from 'components'

import './ShareEntity.css';



export default class ShareEntity extends React.Component {

  constructor(props) {
    super(props);

    this.shareEntity = this.props.shareEntity;
    this.revokeEntitySharing = this.props.revokeEntitySharing;
    this.getSharedEntity = this.props.getSharedEntity;

    this.state = {
      loaded: false, 
      checked: false,
      resourceId: this.props.resourceId,
      id: ''
    }

    this.loadSharedEntity();
  }

  loadSharedEntity = async () => {
    const id = await this.getSharedEntity(this.props.resourceId);
    this.setState(
      {
        id,
        resourceId: this.props.resourceId,
        checked: id
      }
    );
    this.setState({
      loaded: true
    })
  }

  toggleValue = async ({target: {checked}}) => {
    this.setState(prevState => {
        return {
          checked
        };
    });
    if(checked) {
      const id = await this.shareEntity(this.state.resourceId);
      this.setState({id});
    } else {
      await this.revokeEntitySharing(this.state.id);
      this.setState({id: ''});
    }
  }

  buildShareLink = () => {
    if(this.state.id) {
      return `${window.location.origin}/share/${this.props.type}/${this.state.id}`;
    } else {
      return '';
    }
  }
  
  render() {
    if(!this.state.loaded) {
      return <div className='ShareEntity__loading-indicator'>loading...</div>;
    }

    return (
      <div className='ShareEntity'>
        <form>
          <div className='ShareEntity__enable'>
            <div className='ShareEntity__enable-text' >Enable sharing </div>
            <div className='ShareEntity__enable-switch'><Switch checked={this.state.checked} onChange={this.toggleValue}/></div>
          </div>
          <div className='ShareEntity__clipboard'>
            <CopyToClipboard disabled={!this.state.checked} value={this.buildShareLink()} />
          </div>
        </form>
      </div>
    );
  }
}