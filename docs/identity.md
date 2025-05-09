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
  Application {
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
  Mapping {
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

  Mapping }o..o{ Tenant: "assigned"
  User ||--o{ Authorization: "granted"
  Authorization ||--|{ Permission: "granted"
  Group }o..o{ Tenant: "assigned"
  Group }o..o{ Role: "assigned"
  Group ||--o{ Authorization: "granted"
  Role ||--o{ Authorization: "granted"
  User }o..o{ Group: "member"
  User }o..o{ Role: "assigned"
  User }o..o{ Tenant: "assigned"
  Mapping ||--o{ Authorization: "granted"
  Mapping }o..o{ Role: "assigned"
  Mapping }o..o{ Group: "member"
  Role }o--o{ Tenant: "assigned"
  Application ||--o{ Authorization: "granted"
  Application }o..o{ Group: "member"
  Application }o..o{ Role: "assigned"
  Application }o..o{ Tenant: "assigned"
```

### Unmanaged entities

Under the "simple mapping" feature, users and applications are not managed as their own entities.
All relationships such as group, role and tenant membership, and assigned authorizations, are purely
based on the username or application id.
