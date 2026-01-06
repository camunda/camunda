# Identity

## Data model

```mermaid
erDiagram
  User {
    string username PK
    string name
    string email
    string password
  }
  Client {
    string id PK
  }
  Role {
    string id PK
    string name
  }
  Group {
    string id PK
    string name
  }
  Tenant {
    string id PK
    string name
    string description
  }
  MappingRule {
    string id PK
    string name
    string claimName
    string claimValue
  }
  Authorization {
    string ownerId
    enum ownerType
    enum resourceType
  }
  Permission {
    enum permissionType
    string[] resourceIds
  }

  MappingRule }o..o{ Tenant: "assigned"
  User ||--o{ Authorization: "granted"
  Authorization ||--|{ Permission: "granted"
  Group }o..o{ Tenant: "assigned"
  Group }o..o{ Role: "assigned"
  Group ||--o{ Authorization: "granted"
  Role ||--o{ Authorization: "granted"
  User }o..o{ Group: "member"
  User }o..o{ Role: "assigned"
  User }o..o{ Tenant: "assigned"
  MappingRule ||--o{ Authorization: "granted"
  MappingRule }o..o{ Role: "assigned"
  MappingRule }o..o{ Group: "member"
  Role }o--o{ Tenant: "assigned"
  Client ||--o{ Authorization: "granted"
  Client }o..o{ Group: "member"
  Client }o..o{ Role: "assigned"
  Client }o..o{ Tenant: "assigned"
```

### Unmanaged entities

Under the "simple mapping rule" feature, users and clients are not managed as their own entities.
All relationships such as group, role and tenant membership, and assigned authorizations, are purely
based on the username or client id.
