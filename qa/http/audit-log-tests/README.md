# Audit Log Tests

Comprehensive HTTP-based test suite for Camunda audit log functionality.

**87 tests** organized in 11 categories covering process instance creation, modifications, batch operations, search/query, validation, edge cases, security, and multi-tenancy.

---

## 🚀 Quick Start

### Prerequisites

- Running Camunda instance at `http://localhost:8080`
- HTTP client (IntelliJ IDEA or VS Code with REST Client)
- Environment variables: `ZEEBE_REST_ADDRESS_LOCAL`, `BASIC_AUTH_TOKEN`

### Configuration

```json
{
  "local": {
    "ZEEBE_REST_ADDRESS_LOCAL": "http://localhost:8080",
    "BASIC_AUTH_TOKEN": "your-base64-token"
  }
}
```

### Run Tests

1. Open `audit-log-tests.http` in your HTTP client
2. Click "Run All Requests" (tests must run sequentially)
3. Or run individual categories/tests as needed

⚠️ **Important**: Run tests in order - they depend on each other for variable extraction.

---

## 📋 Test Categories

|           Category           |  Tests  |                      Description                      |
|------------------------------|---------|-------------------------------------------------------|
| **1. Setup**                 | 001-010 | Deploy processes and forms, create test instances     |
| **2. Basic Operations**      | 011-015 | Single modifications (activate, terminate, variables) |
| **3. Batch Operations**      | 016-028 | Batch modifications and tracking                      |
| **4. Search & Query**        | 024-032 | Filtering, sorting, pagination                        |
| **5. Data Validation**       | 033-056 | Schema, timestamps, duplicates, actor info            |
| **6. Edge Cases**            | 035-048 | Invalid inputs, error handling                        |
| **7. Authorization**         | 066-068 | Security and access control                           |
| **8. Lifecycle**             | 073-076 | Create → Modify → Cancel flow                         |
| **9. Concurrent Operations** | 069-072 | Race conditions, concurrent access                    |
| **10. Complex Scenarios**    | 057-065 | Multi-step modifications                              |
| **11. Multi-Tenant**         | 082-087 | Tenant isolation and cross-tenant access              |

---

## 📖 Key Test Examples

### Setup (001-010)

- **001**: Deploy user task form
- **002-003**: Deploy test processes (single & batch)
- **004-010**: Create process instances for testing

### Basic Operations (011-015)

- **011**: Activate element in process instance
- **012**: Get element instance key
- **013**: Terminate element
- **014**: Add variables with activation

### Search & Query (024-032)

- **024**: Empty search (all logs)
- **025**: Filter by process instance
- **029**: Filter by operation type
- **031**: Sort ascending/descending
- **032**: Pagination

### Edge Cases (037-050)

- **037**: Malformed process instance key
- **038**: Negative instance key
- **040**: Invalid operation type
- **044**: Negative pagination limit
- **049**: Non-existent process instance

### Multi-Tenant (082-087)

- **083**: Create instance for tenant A
- **085**: Search with correct tenant ID
- **086**: Search with wrong tenant ID (empty results)
- **087**: Verify tenant isolation

---

## 🐛 Troubleshooting

|      Problem       |                   Solution                   |
|--------------------|----------------------------------------------|
| 401 Unauthorized   | Check `BASIC_AUTH_TOKEN` is set correctly    |
| Variable not found | Run Category 1 (Setup) tests first           |
| Tests timeout      | Increase wait times for eventual consistency |
| 404 audit logs     | Enable audit log in Camunda config           |
| Batch not found    | Wait longer for batch completion             |

---

## 📁 Resources

```
audit-log-tests/
├── audit-log-tests.http              # Main test file (87 tests, 3000+ lines)
└── resources/
    ├── single_audit_log_test_process.bpmn
    ├── batch_audit_log_test_process.bpmn
    └── formA.form
```

---

**Last Updated**: December 19, 2025 | **Version**: 2.1 (Compact)

