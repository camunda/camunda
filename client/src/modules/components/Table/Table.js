import React from 'react';
import {Link} from 'react-router-dom';
import {Button} from 'components';

export default function Table({data}) {
  return (<table>
    <thead></thead>
    <tbody>
      {data.map((row, idx) => {
        return (<tr key={idx}>
          {row.map((cell, idx) => {
            return (<td key={idx}>
              {Table.renderCell(cell)}
            </td>);
          })}
        </tr>);
      })}
    </tbody>
  </table>);
}

Table.renderCell = cell => {
  if(typeof cell !== 'object') {
    return cell;
  }

  if(cell.link) {
    return (<Link to={cell.link} className={cell.className}>
      {cell.content}
    </Link>);
  }

  if(cell.onClick) {
    return (<Button onClick={cell.onClick} className={cell.className}>{cell.content}</Button>);
  }
}
