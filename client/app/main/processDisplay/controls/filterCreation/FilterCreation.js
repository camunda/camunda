import {jsx} from 'view-utils';

export function FilterCreation() {
  return <td>
   <div className="btn-group">
     <button type="button" className="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
       + <span className="caret"></span>
     </button>
     <ul className="dropdown-menu">
       <li><a href="#">Start Time</a></li>
       <li><a href="#">Variable</a></li>
       <li><a href="#">Flow Node</a></li>
     </ul>
   </div>
  </td>;
}
