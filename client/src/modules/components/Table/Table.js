import React from 'react';
import {Link} from 'react-router-dom';
import {Button} from 'components';

import './Table.css';

export default function Table({head, body, foot}) {
  return (
    <div className='Table'>
      <table className='Table__content'>
        <thead>{Table.renderRow(head, 0)}</thead>
        <tbody>
          {body.map((row, idx) => {
            return Table.renderRow(row, idx);
          })}
        </tbody>
        <tfoot>{Table.renderRow(foot, 0)}</tfoot>
      </table>
    </div>);
}

Table.renderRow = (row, idx) => {
  if (row) {
    return (<tr key={idx}>
      {row.map((cell, idx) => {
        return (<td key={idx}>
          {Table.renderCell(cell)}
        </td>);
      })}
    </tr>);
  }
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
