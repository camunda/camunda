/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useState, useMemo} from 'react';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableContainer as CarbonTableContainer,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
  Button,
  Tag,
  IconButton,
} from '@carbon/react';
import {Search, View} from '@carbon/icons-react';
import {formatDate} from 'modules/utils/date/formatDate';
import {Container, TableContainer, ActionCell} from 'App/AuditLog/AuditLogTable/styled';
import {mockAuditLogData, type AuditLogEntry} from '../mocks';
import {CommentModal} from '../CommentModal';

const getOperationTagKind = (operation: string) => {
  switch (operation) {
    case 'Migrate':
      return 'blue';
    case 'Modify':
      return 'cyan';
    case 'Retry':
      return 'green';
    case 'Cancel':
      return 'red';
    case 'Delete':
      return 'red';
    default:
      return 'gray';
  }
};

const getStatusDisplayText = (status: string) => {
  if (status.includes('progress')) return status;
  if (status.includes('success') && status.includes('fail')) return status;
  if (status.includes('instance')) return status;
  if (status.includes('success')) return status;
  if (status.includes('fail')) return status;
  return status;
};

const AuditLogTable: React.FC = observer(() => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedEntry, setSelectedEntry] = useState<AuditLogEntry | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [auditLogData, setAuditLogData] = useState(mockAuditLogData);

  const headers = [
    {key: 'processDefinition', header: 'Process definition'},
    {key: 'operation', header: 'Operation'},
    {key: 'status', header: 'Status'},
    {key: 'startTimestamp', header: 'Start timestamp'},
    {key: 'user', header: 'User'},
    {key: 'comment', header: 'Comment'},
    {key: 'actions', header: 'Actions'},
  ];

  // Filter data based on search term (search in user and comment fields)
  const filteredData = useMemo(() => {
    if (!searchTerm.trim()) {
      return auditLogData;
    }

    const lowercaseSearch = searchTerm.toLowerCase();
    return auditLogData.filter(
      (entry) =>
        entry.user.toLowerCase().includes(lowercaseSearch) ||
        entry.comment.toLowerCase().includes(lowercaseSearch),
    );
  }, [searchTerm, auditLogData]);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(event.target.value);
  };

  const handleViewComment = (entry: AuditLogEntry) => {
    setSelectedEntry(entry);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedEntry(null);
  };

  const handleSaveComment = (entryId: string, newComment: string) => {
    setAuditLogData(prevData =>
      prevData.map(entry =>
        entry.id === entryId ? {...entry, comment: newComment} : entry
      )
    );
  };

  if (auditLogData.length === 0) {
    return (
      <Container>
        <EmptyMessage
          message="No audit logs found"
          additionalInfo="There are no audit log entries to display."
        />
      </Container>
    );
  }

  return (
    <Container>
      <TableContainer>
        <CarbonTableContainer
        >
          <TableToolbar>
            <TableToolbarContent>
              <TableToolbarSearch
                persistent
                onChange={handleSearchChange}
                placeholder="Search user or comment"
                size="md"
                value={searchTerm}
              />
              <Button
                kind="primary"
                size="lg"
                renderIcon={Search}
                iconDescription="Search"
                onClick={() => {}}
              >
                Search
              </Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table size="md">
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader key={header.key}>{header.header}</TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredData.length === 0 && searchTerm.trim() ? (
                <TableRow>
                  <TableCell colSpan={headers.length}>
                    <EmptyMessage
                      message="No matching audit logs found"
                      additionalInfo={`No results found for "${searchTerm}". Try adjusting your search terms.`}
                    />
                  </TableCell>
                </TableRow>
              ) : (
                filteredData.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell>{entry.processDefinition}</TableCell>
                    <TableCell>
                      <Tag size="sm" type={getOperationTagKind(entry.operation)}>
                        {entry.operation}
                      </Tag>
                    </TableCell>
                    <TableCell>{getStatusDisplayText(entry.status)}</TableCell>
                    <TableCell>{formatDate(entry.startTimestamp)}</TableCell>
                    <TableCell>{entry.user}</TableCell>
                    <TableCell>{entry.comment}</TableCell>
                    <TableCell>
                      <ActionCell>
                        <IconButton
                          kind="ghost"
                          label="View comment"
                          size="sm"
                          onClick={() => handleViewComment(entry)}
                        >
                          <View />
                        </IconButton>
                      </ActionCell>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CarbonTableContainer>
      </TableContainer>
      
      <CommentModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        auditLogEntry={selectedEntry}
        onSaveComment={handleSaveComment}
      />
    </Container>
  );
});

export {AuditLogTable};