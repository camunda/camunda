import React from 'react';
import {Link} from 'react-router-dom';

export default function Table({data}) {
  return (<table>
    <thead></thead>
    <tbody>
      {data.map((row, idx) => {
        return (<tr key={idx}>
          {row.map((cell, idx) => {
            return (<td key={idx}>
              {renderCell(cell)}
            </td>);
          })}
        </tr>);
      })}
    </tbody>
  </table>);
}

function renderCell(cell) {
  if(typeof cell !== 'object') {
    return cell;
  }

  if(cell.link) {
    return (<Link to={cell.link} className={cell.className}>
      {cell.content}
    </Link>);
  }

  if(cell.onClick) {
    return (<button onClick={cell.onClick} className={cell.className}>{cell.content}</button>);
  }
}
