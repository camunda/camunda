import {jsx} from 'view-utils';
import {Header} from './header';
import {Footer} from './footer';

export function Main() {
  return <div className="container">
    <Header/>
    <div className="content">
      Yay!
    </div>
    <Footer/>
  </div>;
}
