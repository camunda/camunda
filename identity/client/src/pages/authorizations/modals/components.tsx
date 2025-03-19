import styled from "styled-components";

export const Row = styled.div`
  display: grid;
  width: 100%;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  grid-template-columns: 1fr 2fr;
`;

export const TextFieldContainer = styled.div`
  margin-top: 3px;
`;

export const PermissionsSectionLabel = styled.div`
  font-size: 0.75rem;
  > a {
    font-size: 0.75rem;
  }
  color: var(--cds-text-secondary);
`;
