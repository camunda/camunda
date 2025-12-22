# Audit Log Tests

HTTP-based test suite for Camunda audit log functionality with **87 tests** organized in 11 categories.

## Quick Start

### Prerequisites

- Running Camunda instance at `http://localhost:8080`
- HTTP client (IntelliJ IDEA or VS Code with REST Client extension)
- Authentication configured (Basic Auth for local, Bearer Token for SaaS)

### Configuration

Copy `http-client.env.json.example` to `http-client.private.env.json` and configure:

```json
{
  "local": {
    "ZEEBE_REST_ADDRESS": "http://localhost:8080",
    "BASIC_AUTH_TOKEN": "ZGVtbzpkZW1v",
    "AUTH_TYPE": "Basic"
  }
}
```

**Note**: `BASIC_AUTH_TOKEN` is base64 encoded `username:password` (e.g., `echo -n 'demo:demo' | base64`)

### Run Tests

1. Open `audit-log-tests.http` in your HTTP client
2. Select environment (local/saas/production)
3. Run tests sequentially (they depend on each other)

⚠️ **Important**: Tests must run in order for variable extraction between tests.

## Test Categories

| Category | Tests | Description |
|----------|-------|-------------|
| **Setup** | 001-010 | Deploy processes/forms, create instances |
| **Basic Operations** | 011-015 | Single modifications (activate, terminate, variables) |
| **Batch Operations** | 016-023 | Batch modifications and tracking |
| **Search & Query** | 024-033 | Filtering, sorting, pagination |
| **Data Validation** | 034-056 | Schema, timestamps, duplicates, actor info |
| **Edge Cases** | 037-050 | Invalid inputs, error handling |
| **Authorization** | 066-068 | Security and access control |
| **Lifecycle** | 073-076 | Create → Modify → Cancel flow |
| **Concurrent Operations** | 069-072 | Race conditions, concurrent access |
| **Complex Scenarios** | 057-065 | Multi-step modifications |
| **Multi-Tenant** | 082-087 | Tenant isolation and cross-tenant access |

## Troubleshooting

| Problem | Solution |
|---------|----------|
| 401 Unauthorized | Check authentication token is set correctly |
| Variable not found | Run Setup tests (001-010) first |
| Tests timeout | Increase wait times for eventual consistency |
| 404 audit logs | Enable audit log in Camunda configuration |

## Resources

```
audit-log-tests/
├── audit-log-tests.http              # Main test file (87 tests)
├── README.md                          # This file
├── http-client.env.json.example      # Example environment config
└── resources/
    ├── single_audit_log_test_process.bpmn
    ├── batch_audit_log_test_process.bpmn
    └── formA.form
```

---

**Last Updated**: December 22, 2025 | **Version**: 2.2


