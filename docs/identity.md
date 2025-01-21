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
  }
  Authorization {
    enum ownerType
    enum resourceType
  }
  Permission {
    enum permissionType
    string[] resourceIds
  }

  User }o..o{ Tenant: "assigned"
  User }o..o{ Group: "member"
  User ||--o{ Authorization: "granted"
  Authorization ||--|{ Permission: "granted"
  Group }o..o{ Tenant: "assigned"
  Group }o..o{ Role: "assigned"
  User }o..o{ Role: "assigned"
  Group ||--o{ Authorization: "granted"
  Role ||--o{ Authorization: "granted"
```

