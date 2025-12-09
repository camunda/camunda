# QA Epic Sub-Issues for Audit Log

This document outlines the QA sub-issues to be created for the Audit Log QA Epic in the product-hub repository.

## Overview

- **Parent QA Epic**: https://github.com/camunda/product-hub/issues/3178
- **Engineering Epic**: https://github.com/camunda/camunda/issues/39044
- **Target Repository**: camunda/product-hub

## Breakdown Structure

The QA sub-issues mirror the engineering epic breakdown from issue #39044. Each milestone (M1-M6) has a corresponding QA epic that covers comprehensive testing for that milestone's features.

### M1: QA - Core Audit Log Tracking

**Engineering Epic**: https://github.com/camunda/camunda/issues/40519

**Title**: `QA: Audit Log M1 - Core audit log tracking`

**Key QA Areas**:
- Process Instance Modification audit log handler testing
- REST API endpoints (search, get) validation
- Permissions and authorization testing
- Java client search command verification
- Authentication data in Zeebe records validation
- RDBMS exporter handler testing
- ILM policies and data cleanup verification
- Configurable audit log tracking testing
- Authorization resource types and permissions validation
- Performance and load testing

**Estimate**: 3-4 weeks

**Labels**: `qa`, `audit-log`

---

### M2: QA - Track Process Operations

**Engineering Epic**: https://github.com/camunda/camunda/issues/40534

**Title**: `QA: Audit Log M2 - Track process operations`

**Key QA Areas**:
- Batch process operations (cancel, resolve, migrate, modify)
- Batch management operations (cancel, suspend, resume)
- Migrate process instance operations
- Cancel process instance operations
- Create process instance operations
- Resolve incident operations
- Evaluate decision operations
- Variable update operations

**Estimate**: 2-3 weeks

**Labels**: `qa`, `audit-log`

---

### M3: QA - Track Identity Operations

**Engineering Epic**: https://github.com/camunda/camunda/issues/40538

**Title**: `QA: Audit Log M3 - Track Identity operations`

**Key QA Areas**:
- User operations (create, update, delete)
- Tenant operations (create, update, delete, assign/unassign)
- Role operations (create, update, delete, assign/unassign)
- Mapping rule operations (create, update, delete)
- Group operations (create, update, delete, assign/unassign)
- Authorization operations (create, update, delete)

**Estimate**: 2-3 weeks

**Labels**: `qa`, `audit-log`, `identity`

---

### M4: QA - Track User Task Operations

**Engineering Epic**: https://github.com/camunda/camunda/issues/40536

**Title**: `QA: Audit Log M4 - Track user task operations`

**Key QA Areas**:
- UserTaskRecord changelog testing
- User task operations (assign, unassign, update, complete)
- Change tracking validation
- Camunda Exporter and RDBMS handlers consistency

**Estimate**: 1-2 weeks

**Labels**: `qa`, `audit-log`, `tasklist`

---

### M5: QA - Track Resource Operations

**Engineering Epic**: https://github.com/camunda/camunda/issues/41500

**Title**: `QA: Audit Log M5 - Track resource operations`

**Key QA Areas**:
- Process CREATED and DELETED operations
- DRG CREATED and DELETED operations
- Decision CREATED and DELETED operations
- Form CREATED and DELETED operations
- Resource CREATED and DELETED operations
- Operate General audit log UI validation

**Estimate**: 1-2 weeks

**Labels**: `qa`, `audit-log`, `operate`

---

### M6: QA - Add Operation Details (Should Have)

**Engineering Epic**: https://github.com/camunda/camunda/issues/41196

**Title**: `QA: Audit Log M6 - Add operation details (Should Have)`

**Key QA Areas**:
- To be defined based on implementation
- This is a deprioritized epic for 8.9 iteration

**Estimate**: 1 week (if implemented)

**Labels**: `qa`, `audit-log`, `should-have`

---

## How to Use the Script

### Prerequisites

1. Install the GitHub CLI (`gh`):
   ```bash
   # macOS
   brew install gh
   
   # Linux
   # See https://github.com/cli/cli/blob/trunk/docs/install_linux.md
   
   # Windows
   # See https://github.com/cli/cli/blob/trunk/docs/install_windows.md
   ```

2. Authenticate with GitHub:
   ```bash
   gh auth login
   ```

3. Ensure you have write access to the `camunda/product-hub` repository.

### Running the Script

1. **Interactive Mode** (with prompts):
   ```bash
   ./scripts/create-qa-epic-subissues.sh
   ```
   
   The script will:
   - Display all issue creation commands
   - Ask if you want to create the issues
   - Create the issues if you respond "yes"

2. **Save Commands to File**:
   ```bash
   ./scripts/create-qa-epic-subissues.sh > qa-issues-commands.txt 2>&1
   ```
   
   Then you can review and run the commands manually.

3. **Manual Issue Creation**:
   
   If you prefer to create issues manually, you can copy the GitHub CLI commands from the script output. For example:
   
   ```bash
   gh issue create --repo camunda/product-hub \
     --title 'QA: Audit Log M1 - Core audit log tracking' \
     --body '<issue body content>' \
     --label 'qa,audit-log'
   ```

### Post-Creation Steps

After creating the sub-issues:

1. **Link to Parent Epic**: Ensure all created issues are linked to the parent epic (https://github.com/camunda/product-hub/issues/3178)

2. **Update Parent Epic Description**: Add links to the newly created QA sub-issues in the parent epic's breakdown section

3. **Assign Team Members**: Assign appropriate QA team members to each sub-issue

4. **Set Milestones**: If applicable, set the target milestone/version for each sub-issue based on the engineering epic schedule

5. **Add to Project Board**: Add the issues to the relevant project board for tracking

## Test Coverage Summary

Each QA epic should include:

- **Unit Tests Review**: Review existing unit tests for completeness
- **Integration Tests**: Test component interactions
- **E2E Tests**: End-to-end user workflows
- **API Tests**: REST API and client library validation
- **UI Tests**: Frontend component testing (where applicable)
- **Performance Tests**: Load and stress testing
- **Security Tests**: Authorization and permission validation
- **Acceptance Tests**: Business requirement validation

## Total Estimated QA Effort

- M1: 3-4 weeks
- M2: 2-3 weeks
- M3: 2-3 weeks
- M4: 1-2 weeks
- M5: 1-2 weeks
- M6: 1 week (if implemented)

**Total**: ~10-15 weeks of QA effort (can be parallelized across team members)

## Related Documentation

- Engineering Epic: https://github.com/camunda/camunda/issues/39044
- Parent QA Epic: https://github.com/camunda/product-hub/issues/3178
- Product Epic: https://github.com/camunda/product-hub/issues/1732
- Documentation: https://github.com/camunda/camunda-docs/issues/6925

## Notes

- The script follows the same breakdown structure as the engineering epic (#39044)
- All QA scopes are derived from the engineering breakdown items
- Each QA epic maintains traceability to its corresponding engineering epic
- Test estimates are based on the complexity and scope of each milestone
- M6 is marked as "Should Have" and may be deprioritized based on the 8.9 iteration timeline
